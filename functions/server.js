const express = require("express");
const admin = require("firebase-admin");
const crypto = require("crypto");
const cors = require("cors");
require("dotenv").config();

const app = express();
app.use(express.json());
app.use(cors());

// Initialize Firebase Admin
const serviceAccount = {
  type: "service_account",
  project_id: process.env.FIREBASE_PROJECT_ID,
  private_key_id: process.env.FIREBASE_PRIVATE_KEY_ID,
  private_key: process.env.FIREBASE_PRIVATE_KEY ? process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n") : undefined,
  client_email: process.env.FIREBASE_CLIENT_EMAIL,
  client_id: process.env.FIREBASE_CLIENT_ID,
  auth_uri: "https://accounts.google.com/o/oauth2/auth",
  token_uri: "https://oauth2.googleapis.com/token",
  auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
  client_x509_cert_url: process.env.FIREBASE_CLIENT_X509_CERT_URL
};

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

const db = admin.firestore();

const DEFAULT_IPN_FIELDS = [
  "amount",
  "extraData",
  "message",
  "orderId",
  "orderInfo",
  "orderType",
  "partnerCode",
  "payType",
  "requestId",
  "responseTime",
  "resultCode",
  "transId"
];

const DEFAULT_MOMO_CREATE_URL = "https://test-payment.momo.vn/v2/gateway/api/create";

function normalizeTransferContent(value) {
  if (!value) return "";
  return String(value)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/\s+/g, "")
    .replace(/[^A-Z0-9]/g, "");
}

function parseFieldListFromEnv() {
  const raw = process.env.MOMO_IPN_FIELDS || "";
  if (!raw.trim()) return DEFAULT_IPN_FIELDS;
  return raw
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

function buildRawSignature(payload, fieldList) {
  return fieldList.map((key) => `${key}=${payload[key] ?? ""}`).join("&");
}

function parseExtraData(extraData) {
  if (!extraData || typeof extraData !== "string") return {};

  const tryParseJson = (text) => {
    try {
      const parsed = JSON.parse(text);
      return typeof parsed === "object" && parsed !== null ? parsed : {};
    } catch (_) {
      return {};
    }
  };

  const directJson = tryParseJson(extraData);
  if (Object.keys(directJson).length > 0) {
    return directJson;
  }

  try {
    const decoded = Buffer.from(extraData, "base64").toString("utf8");
    const decodedJson = tryParseJson(decoded);
    if (Object.keys(decodedJson).length > 0) {
      return decodedJson;
    }
  } catch (_) {}

  return { raw: extraData };
}

function resolveTransferContent(payload) {
  const extraData = parseExtraData(payload.extraData);
  const candidates = [
    extraData.transferContent,
    extraData.transfer_content,
    extraData.transferCode,
    payload.orderInfo,
    payload.orderId,
    payload.requestId
  ];

  const transferContent = candidates.find((x) => typeof x === "string" && x.trim().length > 0) || "";

  return {
    transferContent,
    transferContentNormalized: normalizeTransferContent(transferContent)
  };
}

function isMomoSuccess(payload) {
  return String(payload.resultCode) === "0";
}

function shouldVerifySignature() {
  return String(process.env.MOMO_VERIFY_SIGNATURE || "true").toLowerCase() !== "false";
}

function verifyMomoSignature(payload) {
  const incomingSignature = String(payload.signature || "").trim();
  if (!incomingSignature) {
    return { ok: false, reason: "Missing signature" };
  }

  const secretKey = process.env.MOMO_SECRET_KEY || "";
  if (!secretKey) {
    return { ok: false, reason: "MOMO_SECRET_KEY is not configured" };
  }

  const fieldList = parseFieldListFromEnv();
  const rawSignature = buildRawSignature(payload, fieldList);
  const calculated = crypto.createHmac("sha256", secretKey).update(rawSignature).digest("hex");
  const ok = calculated === incomingSignature;

  return {
    ok,
    reason: ok ? "OK" : "Signature mismatch"
  };
}

function requireEnv(name) {
  const value = String(process.env[name] || "").trim();
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function buildCreateSignaturePayload(payload) {
  return [
    `accessKey=${payload.accessKey}`,
    `amount=${payload.amount}`,
    `extraData=${payload.extraData}`,
    `ipnUrl=${payload.ipnUrl}`,
    `orderId=${payload.orderId}`,
    `orderInfo=${payload.orderInfo}`,
    `partnerCode=${payload.partnerCode}`,
    `redirectUrl=${payload.redirectUrl}`,
    `requestId=${payload.requestId}`,
    `requestType=${payload.requestType}`
  ].join("&");
}

async function createMomoPayment(payload) {
  const endpoint = String(process.env.MOMO_CREATE_ENDPOINT || DEFAULT_MOMO_CREATE_URL).trim();
  const partnerCode = requireEnv("MOMO_PARTNER_CODE");
  const accessKey = requireEnv("MOMO_ACCESS_KEY");
  const secretKey = requireEnv("MOMO_SECRET_KEY");
  const redirectUrl = requireEnv("MOMO_REDIRECT_URL");
  const ipnUrl = requireEnv("MOMO_IPN_URL");

  const amount = Math.max(0, Number(payload.amount || 0));
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error("amount must be greater than 0");
  }

  const orderId = String(payload.orderId || "").trim();
  if (!orderId) {
    throw new Error("orderId is required");
  }

  const transferContent = String(payload.transferContent || "").trim();
  const requestId = `${orderId}_${Date.now()}`;

  const extraDataRaw = JSON.stringify({
    orderId,
    transferContent,
    source: "LMS_ANDROID"
  });
  const extraData = Buffer.from(extraDataRaw, "utf8").toString("base64");
  const orderInfo = String(payload.orderInfo || `Thanh toan don hang ${orderId}`).trim();
  const defaultRequestType = String(process.env.MOMO_REQUEST_TYPE || "captureWallet").trim();
  const requestType = String(payload.requestType || defaultRequestType).trim();

  const signaturePayload = {
    accessKey,
    amount: String(Math.round(amount)),
    extraData,
    ipnUrl,
    orderId,
    orderInfo,
    partnerCode,
    redirectUrl,
    requestId,
    requestType
  };

  const rawSignature = buildCreateSignaturePayload(signaturePayload);
  const signature = crypto
    .createHmac("sha256", secretKey)
    .update(rawSignature)
    .digest("hex");

  const requestBody = {
    partnerCode,
    partnerName: process.env.MOMO_PARTNER_NAME || "LMS",
    storeId: process.env.MOMO_STORE_ID || "LMSStore",
    requestId,
    amount: String(Math.round(amount)),
    orderId,
    orderInfo,
    redirectUrl,
    ipnUrl,
    lang: process.env.MOMO_LANG || "vi",
    requestType,
    autoCapture: String(process.env.MOMO_AUTO_CAPTURE || "true").toLowerCase() !== "false",
    extraData,
    orderGroupId: "",
    signature
  };

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(requestBody)
  });

  const json = await response.json();

  await db.collection("momoRequests").doc(requestId).set({
    orderId,
    requestId,
    amount: amount,
    transferContent,
    transferContentNormalized: normalizeTransferContent(transferContent),
    requestBody,
    responseBody: json,
    status: Number(json.resultCode) === 0 ? "CREATED" : "FAILED",
    createdAt: Date.now()
  });

  return {
    ok: Number(json.resultCode) === 0,
    response: json,
    requestId
  };
}

async function upsertBankTransaction(payload) {
  const transId = String(payload.transId || "").trim();
  const requestId = String(payload.requestId || "").trim();
  const fallbackId = crypto.createHash("sha1").update(JSON.stringify(payload)).digest("hex").slice(0, 16);
  const docId = transId ? `momo_${transId}` : requestId ? `momo_req_${requestId}` : `momo_hash_${fallbackId}`;

  const amount = Number(payload.amount || 0);
  const { transferContent, transferContentNormalized } = resolveTransferContent(payload);
  const success = isMomoSuccess(payload);
  const now = Date.now();

  const record = {
    gateway: "MOMO",
    status: success ? "NEW" : "FAILED",
    amount: Number.isFinite(amount) ? amount : 0,
    transferContent,
    transferContentNormalized,
    orderId: String(payload.orderId || ""),
    requestId: requestId,
    transId: transId,
    resultCode: String(payload.resultCode || ""),
    message: String(payload.message || ""),
    payType: String(payload.payType || ""),
    partnerCode: String(payload.partnerCode || ""),
    source: "MOMO_IPN",
    rawPayload: payload,
    updatedAt: now,
    createdAt: now
  };

  const docRef = db.collection("bankTransactions").doc(docId);
  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(docRef);
    if (snapshot.exists) {
      tx.update(docRef, {
        ...record,
        createdAt: snapshot.get("createdAt") || now
      });
      return;
    }
    tx.set(docRef, record);
  });

  return { docId, status: record.status };
}

// Express Routes
app.post("/createMomoPayment", async (req, res) => {
  const payload = req.body && typeof req.body === "object" ? req.body : {};

  try {
    const momoResult = await createMomoPayment(payload);
    if (!momoResult.ok) {
      console.error("[createMomoPayment] MoMo create failed", momoResult.response);
      return res.status(400).json({
        resultCode: momoResult.response.resultCode || 1,
        message: momoResult.response.message || "Create payment failed",
        data: momoResult.response
      });
    }

    return res.status(200).json({
      resultCode: 0,
      message: "success",
      orderId: payload.orderId,
      requestId: momoResult.requestId,
      payUrl: momoResult.response.payUrl || "",
      deeplink: momoResult.response.deeplink || "",
      qrCodeUrl: momoResult.response.qrCodeUrl || "",
      raw: momoResult.response
    });
  } catch (error) {
    console.error("[createMomoPayment] Internal error", error);
    return res.status(500).json({
      resultCode: 97,
      message: error.message || "Internal error"
    });
  }
});

app.post("/momoIpnWebhook", async (req, res) => {
  const payload = req.body && typeof req.body === "object" ? req.body : {};
  console.log("[momoIpnWebhook] Incoming payload", payload);

  if (shouldVerifySignature()) {
    const verified = verifyMomoSignature(payload);
    if (!verified.ok) {
      console.error("[momoIpnWebhook] Invalid signature", { reason: verified.reason });
      return res.status(400).json({ resultCode: 98, message: `Invalid signature: ${verified.reason}` });
    }
  }

  try {
    const saved = await upsertBankTransaction(payload);
    console.log("[momoIpnWebhook] Saved bank transaction", saved);
    return res.status(200).json({ resultCode: 0, message: "received" });
  } catch (error) {
    console.error("[momoIpnWebhook] Failed to persist transaction", error);
    return res.status(500).json({ resultCode: 97, message: "Internal error" });
  }
});

// Health check
app.get("/health", (req, res) => {
  res.status(200).json({ status: "ok" });
});

// Start server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});

module.exports = app;
