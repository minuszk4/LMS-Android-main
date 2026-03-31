# Deploy Backend lên Render (Miễn phí)

Hướng dẫn triển khai Express backend cho MoMo Payment lên Render - nền tảng miễn phí như Heroku.

## Yêu cầu chuẩn bị

1. Tài khoản GitHub (code đang ở GitHub)
2. Tài khoản Render (dùng GitHub login)
3. Firebase service account key (JSON)

## Bước 1: Lấy Firebase Service Account Key

1. Vào [Firebase Console](https://console.firebase.google.com)
2. Chọn project của bạn
3. Tương tác **Project Settings** (bánh răng) → **Service Accounts**
4. Click **Generate New Private Key**
5. Download file JSON (giữ bảo mật)

## Bước 2: Tạo Render Web Service

1. Vào [render.com](https://render.com)
2. Click **New** → **Web Service**
3. Chọn **GitHub** (hoặc paste repo URL)
4. Chọn repository `LMS-Android-main`
5. Điền thông tin:
   - **Name**: `lms-payment-backend` (tùy ý)
   - **Region**: `Singapore` (gần Việt Nam)
   - **Branch**: `main`
   - **Root Directory**: `functions` (chọn sau khi detect)
   - **Runtime**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free`

6. Click **Create Web Service** rồi chờ deploy xong (~2 phút)
7. Lấy URL domain: `https://lms-payment-backend.onrender.com`

## Bước 3: Cấu hình Environment Variables

Trên trang Render web service vừa tạo:

1. Scroll xuống → **Environment** (cái nữa xanh)
2. Click **Add Environment Variable**
3. Thêm tất cả từ `functions/.env.example`:

### Firebase Config (từ JSON key):
```
FIREBASE_PROJECT_ID = (project_id từ JSON)
FIREBASE_PRIVATE_KEY_ID = (private_key_id từ JSON)
FIREBASE_PRIVATE_KEY = (private_key từ JSON, thay \\n thành thực newline)
FIREBASE_CLIENT_EMAIL = (client_email từ JSON)
FIREBASE_CLIENT_ID = (client_id từ JSON)
FIREBASE_CLIENT_X509_CERT_URL = (client_x509_cert_url từ JSON)
```

### MoMo Config:
```
MOMO_PARTNER_CODE = MOMO
MOMO_ACCESS_KEY = F8BBA842ECF85
MOMO_SECRET_KEY = K951B6PE1waDMi640xX08PD3vg6EkVlz
MOMO_STORE_ID = MomoTestStore
MOMO_PARTNER_NAME = LMS
MOMO_LANG = vi
MOMO_AUTO_CAPTURE = true
MOMO_VERIFY_SIGNATURE = true
MOMO_IPN_FIELDS = accessKey,amount,extraData,message,orderId,orderInfo,orderType,partnerCode,payType,requestId,responseTime,resultCode,transId
MOMO_CREATE_ENDPOINT = https://test-payment.momo.vn/v2/gateway/api/create

MOMO_REDIRECT_URL = https://lms-payment-backend.onrender.com/health
MOMO_IPN_URL = https://lms-payment-backend.onrender.com/momoIpnWebhook

PORT = 3000
```

⚠️ **Lưu ý FIREBASE_PRIVATE_KEY:**
- Copy value `private_key` từ JSON
- Paste vào Render
- Nó sẽ tự xử lý `\n` (newline)

4. Click **Save Changes** → Render sẽ redeploy

## Bước 4: Verify Dịch vụ

```bash
curl https://lms-payment-backend.onrender.com/health
```

Kết quả mong đợi:
```json
{"status":"ok"}
```

## Bước 5: Cập nhật Android App

Trong [`local.properties`](../local.properties):
```properties
MOMO_FUNCTION_BASE_URL=https://lms-payment-backend.onrender.com
```

Rebuild app.

## Bước 6: Test MoMo Webhook (Optional)

Dùng [webhook.site](https://webhook.site) để test:

1. Tạo webhook test tại webhook.site
2. Copy URL
3. Tạm thời đổi `MOMO_IPN_URL` trên Render thành webhook.site
4. Gửi POST thử tới `/momoIpnWebhook`:

```bash
curl -X POST https://lms-payment-backend.onrender.com/momoIpnWebhook \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":"TEST123",
    "amount":"50000",
    "resultCode":"0",
    "message":"success"
  }'
```

## Lưu ý quan trọng

1. **Free tier Render** có cold start (~30 giây sau 15 phút không dùng)
   - App sẽ sleep nếu không dùng
   - Lần đầu gọi sẽ chậm, lần sau nhanh
   - Có thể đổi sang Paid tier sau nếu cần tốc độ ổn định

2. **Dữ liệu persistent**
   - Render không lưu dữ liệu trên disk
   - Dữ liệu được lưu trong Firestore (ổn)

3. **Giới hạn**
   - Free tier: 750 giờ/tháng (đủ dùng)
   - Bandwidth: không giới hạn

4. **Deploy từ GitHub**
   - Khi push code mới lên GitHub (branch `main`)
   - Render tự redeploy
   - Khoảng 1-2 phút xong

## Troubleshooting

### Lỗi "Cannot find module 'express'"
- SSH vào Render
- Chạy `npm install`
- Hoặc trigger redeploy lại

### Lỗi Firebase auth
- Check lại env variable Firebase config
- Đảm bảo private_key có newline thực (`\n` không phải `\\n`)

### IPN webhook không tới
- Check logs Render để xem error
- Đảm bảo `MOMO_IPN_URL` đúng và public

## Xong!

Backend đã chạy trên Render. App Android sẽ gọi:
- `POST /createMomoPayment` để tạo thanh toán
- MoMo webhook gọi `POST /momoIpnWebhook` để xác nhận giao dịch
