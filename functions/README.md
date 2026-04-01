# Firebase Functions - MoMo Payment + IPN

This module exposes 2 Cloud Functions for MoMo sandbox:
- `createMomoPayment`: verifies Firebase ID token, validates order ownership, then signs and creates a payment request with MoMo.
- `momoIpnWebhook`: receives IPN callbacks, verifies signature, stores normalized records in `bankTransactions`, and finalizes pending orders server-side.

Order finalization now happens on server-side webhook processing (not on Android client).

## 1) Install dependencies

```bash
cd functions
npm install
```

## 2) Configure environment

Use `.env.example` as reference.

Required:
- `MOMO_SECRET_KEY`

Optional:
- `MOMO_VERIFY_SIGNATURE` (default `true`)
- `MOMO_IPN_FIELDS` (comma-separated field order for HMAC raw string)

For Firebase Functions v2, set env before deploy:

```bash
firebase functions:config:set momo.secret_key="YOUR_SECRET_KEY"
```

Or set runtime env using your preferred deployment method.

## 3) Deploy

```bash
firebase deploy --only functions
```

Function names:
- `createMomoPayment`
- `momoIpnWebhook`

Endpoint format:
- `https://asia-southeast1-<project-id>.cloudfunctions.net/createMomoPayment`
- `https://asia-southeast1-<project-id>.cloudfunctions.net/momoIpnWebhook`

## 4) Configure MoMo IPN URL

Set MoMo `ipnUrl` to the endpoint above.

## 5) Notes for matching auto-confirm

To ensure order matching is reliable, include transfer content in your MoMo request payload:
- Put `transferContent` in `extraData` JSON (recommended)
- Or include it in `orderInfo`

The webhook tries these fields in order:
1. `extraData.transferContent`
2. `extraData.transfer_content`
3. `extraData.transferCode`
4. `orderInfo`
5. `orderId`
6. `requestId`

Then it normalizes to `transferContentNormalized`.

## Firestore record shape

Collection: `bankTransactions`

Main fields:
- `gateway`: `MOMO`
- `status`: `NEW` when MoMo `resultCode=0`, else `FAILED`
- `amount`: number
- `transferContent`
- `transferContentNormalized`
- `orderId`, `requestId`, `transId`
- `rawPayload`
- `createdAt`, `updatedAt`
