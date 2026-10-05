# Tải và chạy KTPM trên Windows

## 1. Tải dự án

Mở [GitHub KTPM](https://github.com/Loistarva/KTPM/) → **Code → Download ZIP** → giải nén. Mở thư mục có `backend`, `frontend`, `scripts` và `docker-compose.yml`.

Mở PowerShell và vào thư mục đó, ví dụ:

```powershell
cd "C:\Projects\KTPM-main"
```

Thay đường dẫn bằng nơi bạn đã giải nén. Chọn **một** trong hai cách dưới đây.

## 2. Chạy bằng Docker

Cài [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/), mở ứng dụng và đợi engine chạy với Linux containers. Không cần cài riêng Java, Node.js hay PostgreSQL.

Tại thư mục gốc:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
```

Lần đầu cần Internet để tải image/thư viện. Khi backend sẵn sàng, mở **http://localhost:5173**.

Kiểm tra hoặc xem lỗi:

```powershell
docker compose ps
docker compose logs --tail=50 backend frontend
```

Tắt bằng `docker compose down`; lần sau bật bằng `docker compose up -d`. Không thêm `-v` nếu muốn giữ database.

## 3. Chạy trực tiếp, không dùng Docker

Cài **JDK 21** từ [Adoptium](https://adoptium.net/temurin/releases/) (bật PATH/JAVA_HOME) và **Node.js 24 LTS** từ [Node.js](https://nodejs.org/en/download). Mở lại PowerShell sau khi cài. Không cần cài riêng Maven hoặc PostgreSQL.

**Terminal 1**, tại thư mục gốc:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-portable.ps1
```

Đợi backend khởi động. **Terminal 2**, cũng bắt đầu tại thư mục gốc:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Mở **http://localhost:5173**. Giữ cả hai terminal mở; bấm **Ctrl+C** ở từng terminal để tắt. Lần sau chạy lại hai lệnh bật, không cần `npm ci` nếu thư viện không đổi.

## 4. Đăng nhập và sử dụng

| Tài khoản | Mật khẩu |
|---|---|
| admin | Admin123! |
| seller | Seller123! |
| bidder1 / bidder2 | Bidder123! |

Đăng nhập seller để tạo phiên; đăng nhập bidder ở tab khác để đặt giá. Bấm **Làm mới/F5** để cập nhật giá.

Swagger: **http://localhost:8080/swagger-ui/index.html**. Kiểm tra backend: **http://localhost:8080/actuator/health**, kết quả phải là `{"status":"UP"}`.

Không bật Docker và bản trực tiếp cùng lúc vì trùng cổng. Hai cách có database riêng; dữ liệu portable ở `backend/.local/postgres`, dữ liệu Docker ở volume. Không xóa chúng nếu muốn giữ lịch sử.

Nếu repo tải về chưa có cấu trúc trên, người quản lý cần push bản mới lên GitHub. Xem [README](../README.md) để biết chi tiết API và kiểm thử.
