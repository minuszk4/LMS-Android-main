/**
 * Backend webhook va tao phien thanh toan cho du an LMS Android.
 *
 * Nhiem vu chinh:
 * - xac thuc caller truoc khi tao request thanh toan MoMo,
 * - ky request gui di va xac minh chu ky IPN gui ve,
 * - luu vet giao dich vao Firestore,
 * - finalize order thanh cong bang enrollment va payout record.
 */
const express = require("express");
const admin = require("firebase-admin");
const crypto = require("crypto");
const cors = require("cors");
require("dotenv").config();

const app = express();
app.use(express.json());
app.use(cors());

// Khoi tao Firebase Admin mot lan de server co the doc/ghi Firestore
// va xac minh Firebase ID token do Android client gui len.
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

// IPN verification per MoMo spec expects accessKey to be part of the signature payload.
const DEFAULT_IPN_FIELDS = [
  "accessKey",
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

async function authenticateFirebaseUser(req, res, next) {
  // Chi user so huu order moi duoc tao payment session cho order do.
  // Viec xac minh duoc thuc hien o phia server bang Firebase Auth.
  const authHeader = String(req.headers.authorization || "").trim();
  if (!authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ resultCode: 401, message: "Missing bearer token" });
  }

  const idToken = authHeader.slice("Bearer ".length).trim();
  if (!idToken) {
    return res.status(401).json({ resultCode: 401, message: "Missing bearer token" });
  }

  try {
    const decoded = await admin.auth().verifyIdToken(idToken, true);
    req.user = decoded;
    return next();
  } catch (error) {
    console.error("[auth] Invalid Firebase ID token", error);
    return res.status(401).json({ resultCode: 401, message: "Invalid or expired token" });
  }
}

function normalizeTransferContent(value) {
  // Doi soat thanh toan so sanh transfer content o dang da chuan hoa
  // de tranh sai lech do khoang trang, dau tieng Viet hoac ky tu dac biet.
  if (!value) return "";
  return String(value)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/\s+/g, "")
    .replace(/[^A-Z0-9]/g, "");
}

function parseFieldListFromEnv() {
  // Danh sach field ky co the tuy bien qua env, nhung accessKey luon phai
  // co mat vi day la mot phan cua hop dong xac minh IPN voi MoMo.
  const raw = process.env.MOMO_IPN_FIELDS || "";
  const fields = !raw.trim()
    ? DEFAULT_IPN_FIELDS
    : raw
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);

  // Ensure accessKey is always part of the IPN signature field list (MoMo requirement).
  if (!fields.includes("accessKey")) {
    return ["accessKey", ...fields];
  }

  return fields;
}

function buildRawSignature(payload, fieldList, override = {}) {
  return fieldList.map((key) => `${key}=${override[key] ?? payload[key] ?? ""}`).join("&");
}

function parseExtraData(extraData) {
  // MoMo co the tra extraData o dang JSON truc tiep hoac JSON ma hoa base64.
  // Parser nay ho tro ca hai de logic fulfill khong phu thuoc dinh dang.
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
  // Transfer content duoc luu lai ro rang vi se duoc tai su dung trong payout,
  // audit va cac tinh huong troubleshooting tren Firestore.
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
  // Xac minh chu ky de ngan webhook gia mao danh dau order la da thanh toan.
  // Chuoi HMAC phai dung dung thu tu field theo dac ta cua MoMo.
  const incomingSignature = String(payload.signature || "").trim();
  if (!incomingSignature) {
    return { ok: false, reason: "Missing signature" };
  }

  const secretKey = process.env.MOMO_SECRET_KEY || "";
  if (!secretKey) {
    return { ok: false, reason: "MOMO_SECRET_KEY is not configured" };
  }

  const fieldList = parseFieldListFromEnv();
  const needsAccessKey = fieldList.includes("accessKey");
  const accessKey = needsAccessKey ? requireEnv("MOMO_ACCESS_KEY") : undefined;

  const rawSignature = buildRawSignature(payload, fieldList, needsAccessKey ? { accessKey } : {});
  const calculated = crypto.createHmac("sha256", secretKey).update(rawSignature).digest("hex");
  const ok = calculated === incomingSignature;

  console.log("[verifyMomoSignature] Debug info", {
    incomingSignature,
    calculated,
    fieldList,
    rawSignature,
    secretKeyLength: secretKey.length
  });

  return {
    ok,
    reason: ok ? "OK" : "Signature mismatch"
  };
}

function requireEnv(name) {
  // Loi cau hinh can duoc phat hien som, tranh tao ra payment request nua dung
  // nua sai roi moi that bai o giua luong.
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
  // Ham nay tao request da duoc ky gui sang MoMo va luu toan bo cap
  // request/response vao Firestore de phuc vu support va audit.
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
  // Moi webhook deu duoc luu xuong truoc de viec truy vet khong phu thuoc vao
  // viec buoc fulfill phia sau co thanh cong ngay trong request do hay khong.
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

function buildRecommendationEventDocId(eventType, orderId, courseId, userId) {
  const safeEventType = String(eventType || "").trim().toUpperCase();
  const safeOrderId = String(orderId || "").trim();
  const safeCourseId = String(courseId || "").trim();
  const safeUserId = String(userId || "").trim();
  return `${safeEventType}_${safeOrderId}_${safeCourseId}_${safeUserId}`.replace(/[^A-Z0-9_:-]/gi, "_");
}

function buildRecommendationFeedbackEvent({
  eventType,
  orderId,
  userId,
  courseId,
  now,
  source = "payment_success",
}) {
  return {
    id: buildRecommendationEventDocId(eventType, orderId, courseId, userId),
    userId: String(userId || "").trim(),
    courseId: String(courseId || "").trim(),
    predictionLogId: "",
    eventType: String(eventType || "").trim().toUpperCase(),
    source,
    modelVersion: "",
    createdAt: now,
  };
}

async function fulfillOrderForSuccessfulIpn(payload, bankTransactionDocId) {
  // Fulfillment duoc goi trong mot Firestore transaction de enrollment,
  // xac nhan order, tao payout va xoa cart giu duoc tinh nhat quan.
  if (!isMomoSuccess(payload)) {
    return { updated: false, reason: "IPN is not successful" };
  }

  const orderId = String(payload.orderId || "").trim();
  if (!orderId) {
    return { updated: false, reason: "Missing orderId" };
  }

  const amount = Number(payload.amount || 0);
  const now = Date.now();
  const orderRef = db.collection("orders").doc(orderId);
  const bankTxRef = db.collection("bankTransactions").doc(bankTransactionDocId);

  const result = await db.runTransaction(async (tx) => {
    const orderSnapshot = await tx.get(orderRef);
    if (!orderSnapshot.exists) {
      return { updated: false, reason: "Order not found" };
    }

    const order = orderSnapshot.data() || {};
    if (String(order.paymentStatus || "") !== "PENDING") {
      return { updated: false, reason: "Order is not pending" };
    }

    const orderAmount = Number(order.totalAmount || 0);
    if (Number.isFinite(amount) && amount + 0.5 < orderAmount) {
      return { updated: false, reason: "IPN amount is less than order total" };
    }

    const orderItemsQuery = db.collection("orderItems").where("orderId", "==", orderId);
    const orderItemsSnapshot = await tx.get(orderItemsQuery);
    if (orderItemsSnapshot.empty) {
      return { updated: false, reason: "Order items not found" };
    }

    const userId = String(order.userId || "").trim();
    if (!userId) {
      return { updated: false, reason: "Order missing userId" };
    }

    const userSnapshot = await tx.get(db.collection("users").doc(userId));
    const userData = userSnapshot.data() || {};
    const studentName = String(userData.fullName || "").trim();
    const itemContexts = [];

    for (const itemDoc of orderItemsSnapshot.docs) {
      const item = itemDoc.data() || {};
      const courseId = String(item.courseId || "").trim();
      if (!courseId) {
        continue;
      }

      const courseRef = db.collection("courses").doc(courseId);
      const courseSnapshot = await tx.get(courseRef);
      const courseData = courseSnapshot.data() || {};
      const instructorId = String(item.instructorId || courseData.instructorId || "").trim();
      const instructorName = String(item.instructorName || courseData.instructorName || "").trim();
      const courseTitle = String(item.courseTitle || courseData.title || "").trim();
      const courseThumbnailUrl = String(item.courseThumbnailUrl || courseData.thumbnailUrl || "").trim();

      const enrollmentId = `${userId}_${courseId}`;
      const enrollmentRef = db.collection("enrollments").doc(enrollmentId);
      const enrollmentSnapshot = await tx.get(enrollmentRef);
      let instructorData = {};
      let payoutSnapshot = null;
      if (instructorId) {
        const instructorSnapshot = await tx.get(db.collection("instructors").doc(instructorId));
        instructorData = instructorSnapshot.data() || {};
        payoutSnapshot = await tx.get(db.collection("instructorPayouts").doc(itemDoc.id));
      }

      itemContexts.push({
        itemDoc,
        item,
        courseId,
        courseRef,
        enrollmentId,
        enrollmentRef,
        enrollmentExists: enrollmentSnapshot.exists,
        instructorId,
        instructorName,
        courseTitle,
        courseThumbnailUrl,
        instructorData,
        payoutExists: payoutSnapshot ? payoutSnapshot.exists : false
      });
    }

    for (const itemContext of itemContexts) {
      const {
        itemDoc,
        item,
        courseId,
        courseRef,
        enrollmentId,
        enrollmentRef,
        enrollmentExists,
        instructorId,
        instructorName,
        courseTitle,
        courseThumbnailUrl,
        instructorData,
        payoutExists
      } = itemContext;

      if (!enrollmentExists) {
        tx.set(enrollmentRef, {
          id: enrollmentId,
          userId,
          courseId,
          enrolledAt: now
        });

        tx.update(courseRef, {
          enrollmentCount: admin.firestore.FieldValue.increment(1)
        });

        const enrollEvent = buildRecommendationFeedbackEvent({
          eventType: "ENROLL",
          orderId,
          userId,
          courseId,
          now,
          source: "payment_enrollment"
        });
        tx.set(
          db.collection("recommendationFeedbackEvents").doc(enrollEvent.id),
          enrollEvent
        );
      }

      tx.delete(db.collection("cartItems").doc(`${userId}_${courseId}`));

      const purchaseEvent = buildRecommendationFeedbackEvent({
        eventType: "PURCHASE",
        orderId,
        userId,
        courseId,
        now,
        source: "payment_success"
      });
      tx.set(
        db.collection("recommendationFeedbackEvents").doc(purchaseEvent.id),
        purchaseEvent
      );

      if (instructorId && !payoutExists) {
        const bankName = String(instructorData.bankName || "").trim();
        const bankCode = String(instructorData.bankCode || "").trim();
        const bankAccountNumber = String(instructorData.bankAccountNumber || "").trim();
        const bankAccountHolder = String(instructorData.bankAccountHolder || "").trim();
        const hasBankInfo = Boolean(bankName && bankAccountNumber && bankAccountHolder);

        tx.set(db.collection("instructorPayouts").doc(itemDoc.id), {
          id: itemDoc.id,
          orderId,
          orderItemId: itemDoc.id,
          courseId,
          courseTitle,
          courseThumbnailUrl,
          studentId: userId,
          studentName,
          instructorId,
          instructorName,
          grossAmount: Number(item.coursePrice || 0),
          payoutAmount: Number(item.coursePrice || 0),
          payoutStatus: "PENDING",
          orderConfirmedAt: now,
          paidAt: 0,
          paidByAdminUid: "",
          manualTransferReference: "",
          bankName,
          bankCode,
          bankAccountNumber,
          bankAccountHolder,
          hasBankInfo
        });
      }
    }

    tx.update(orderRef, {
      paymentStatus: "SUCCESS",
      confirmedAt: now
    });

    tx.update(bankTxRef, {
      status: "USED",
      consumedOrderId: orderId,
      consumedAt: now,
      updatedAt: now
    });

    return { updated: true, orderId };
  });

  return result;
}

// Express Routes
// Android client goi endpoint nay sau khi da tao order trong Firestore
// va truoc khi dieu huong hoc vien sang luong thanh toan MoMo.
app.post("/createMomoPayment", authenticateFirebaseUser, async (req, res) => {
  const payload = req.body && typeof req.body === "object" ? req.body : {};

  try {
    const orderId = String(payload.orderId || "").trim();
    if (!orderId) {
      return res.status(400).json({ resultCode: 2, message: "orderId is required" });
    }

    const orderSnapshot = await db.collection("orders").doc(orderId).get();
    if (!orderSnapshot.exists) {
      return res.status(404).json({ resultCode: 3, message: "Order not found" });
    }

    const order = orderSnapshot.data() || {};
    if (String(order.userId || "") !== String(req.user.uid || "")) {
      return res.status(403).json({ resultCode: 4, message: "Forbidden for this order" });
    }

    if (String(order.paymentStatus || "") !== "PENDING") {
      return res.status(400).json({ resultCode: 5, message: "Order is not pending" });
    }

    const momoResult = await createMomoPayment({
      orderId,
      amount: Number(order.totalAmount || 0),
      orderInfo: `Thanh toan don hang ${orderId}`,
      transferContent: String(order.transferContent || ""),
      requestType: String(payload.requestType || "captureWallet")
    });
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
  // Day la nguon chan ly phia server cho viec finalize order.
  // Client tuyet doi khong tu danh dau paymentStatus = SUCCESS.
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
    const fulfillment = await fulfillOrderForSuccessfulIpn(payload, saved.docId);
    console.log("[momoIpnWebhook] Saved bank transaction", saved);
    console.log("[momoIpnWebhook] Fulfillment result", fulfillment);
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
