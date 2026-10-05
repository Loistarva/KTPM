# Tải và chạy KTPM trên Windows

## 1. Tải dự án

Mở [GitHub KTPM](https://github.com/Loistarva/KTPM/) → **Code → Download ZIP** → giải nén.

Mở thư mục có `backend`, `frontend`, `scripts`, `docker-compose.yml`. Mở PowerShell và vào thư mục đó:

```powershell
cd "C:\Projects\KTPM-main"
```

Thay đường dẫn bằng nơi bạn giải nén. Chọn **một** cách bên dưới.

## 2. Cách Docker — cài một phần mềm

1. Cài [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/), dùng Linux containers. Nếu yêu cầu WSL hoặc khởi động lại, làm theo trình cài.
2. Mở Docker Desktop, đợi engine chạy; mở lại PowerShell sau khi cài.
3. Tại thư mục dự án, chạy:

```powershell
docker compose up --build -d
```

Đợi tải/build xong rồi mở **http://localhost:5173**. Không cần cài Java, Node.js hay PostgreSQL riêng.

Lần sau bật lại: `docker compose up -d`. Khi sửa code: dùng lệnh có `--build`. Tắt: `docker compose down` — dữ liệu vẫn giữ. Không thêm `-v` nếu muốn giữ database.

## 3. Cách thường — cài hai phần mềm

1. Cài [Java JDK 21](https://adoptium.net/temurin/releases/), chọn Windows JDK; bật tùy chọn PATH và JAVA_HOME trong trình cài.
2. Cài [Node.js 24 LTS](https://nodejs.org/en/download), chọn Windows Installer và giữ các tùy chọn mặc định.
3. Đóng rồi mở lại PowerShell. Không cần cài riêng Maven hay PostgreSQL.

**Cửa sổ PowerShell thứ nhất**, tại thư mục dự án:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-portable.ps1
```

**Cửa sổ PowerShell thứ hai**, cũng bắt đầu tại thư mục dự án:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Đợi backend/frontend khởi động rồi mở **http://localhost:5173**. Giữ cả hai cửa sổ mở; bấm **Ctrl+C** ở từng cửa sổ để tắt. Lần sau chạy lại, bỏ qua `npm.cmd ci` nếu thư viện không đổi.

## 4. Đăng nhập

| Tài khoản | Mật khẩu |
|---|---|
| seller | Seller123! |
| bidder1 / bidder2 | Bidder123! |
| admin | Admin123! |

Seller tạo phiên; bidder đặt giá. Bấm **Làm mới/F5** để xem giá mới.

Swagger thử API: **http://localhost:8080/swagger-ui/index.html**.

Lần đầu cần Internet và thời gian tải thư viện. Không bật hai cách cùng lúc vì trùng cổng. Docker và cách thường dùng database riêng.

Nếu tải repo không thấy các thư mục trên, đó có thể là bản cũ; người quản lý cần push bản mới. Chi tiết và xử lý lỗi xem [README mục 5](../README.md).
