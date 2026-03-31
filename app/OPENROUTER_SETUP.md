# Hướng Dẫn Setup OpenRouter (Miễn Phí)

## 📋 Tại sao dùng OpenRouter?

- ✅ **Miễn phí**: Models như Qwen 3.6 Plus Preview hoàn toàn miễn phí
- ✅ **Không quota**: Không bị giới hạn như Google Gemini free tier
- ✅ **Đa model**: Có nhiều model khác nhau để chọn
- ✅ **Ổn định**: Hoạt động tốt cho chat LMS

## 🚀 Bước 1: Tạo Tài Khoản OpenRouter

1. Vào https://openrouter.ai/
2. Click **"Sign up"** (hoặc đăng nhập Google)
3. Xác minh email

## 🔑 Bước 2: Lấy API Key

1. Đăng nhập vào OpenRouter
2. Vào **Dashboard → "Keys"** (hoặc `https://openrouter.ai/keys`)
3. Click **"Create new"**
4. Copy API Key (dạng `sk-or-...`)

## 📝 Bước 3: Cấu Hình Local Properties

Mở file `local.properties`:

```properties
OPENROUTER_API_KEY=sk-or-xxxxxxxxxxxxxxxxxxxxxxxxxx
```

**⚠️ Lưu ý**: 
- Không share API key công khai
- Giữ file `local.properties` ở `.gitignore`

## 🛠️ Bước 4: Build & Run

```bash
./gradlew clean build
```

Ứng dụng sẽ dùng OpenRouter thay vì Gemini.

## 📊 Models Miễn Phí Được Khuyến Nghị

| Model | Tốc độ | Chất lượng | Free |
|-------|-------|-----------|------|
| qwen/qwen3.6-plus-preview:free | Nhanh | Tốt | ✅ |
| qwen/qwen3.5-turbo | Rất nhanh | Bình thường | ✅ |
| llama-3.3-70b-instruct:free | Trung bình | Tốt | ✅ |

Hiện tại app dùng: **qwen/qwen3.6-plus-preview:free**

## 🔄 Đổi Model (Tùy Chọn)

Sửa trong file `OpenRouterChatbotRepository.kt`:

```kotlin
model = "llama-3.3-70b-instruct:free"  // Đổi model tại đây
```

## ✅ Kiểm Tra

1. Mở app
2. Vào Chat
3. Gửi tin nhắn → Nếu nhận được phản hồi = OK ✅

## ❌ Troubleshooting

**Lỗi "Authorization failed"**
- Kiểm tra API key đúng chưa
- Copy lại từ OpenRouter Keys page

**Lỗi "Model not found"**
- Model name sai hoặc đã bị xóa
- Kiểm tra lại tên model trong `OpenRouterChatbotRepository.kt`

**Chat slow/timeout**
- OpenRouter server có thể bị tải
- Thử lại sau vài phút

## 📚 Tài Liệu Thêm

- OpenRouter Docs: https://openrouter.ai/docs
- Available Models: https://openrouter.ai/docs/models
