# KTPM — API Pha 1

Base URL local: `http://localhost:8080`. Gửi `Content-Type: application/json` khi có body.
JWT đặt tại `Authorization: Bearer <accessToken>`; lấy token thật sau login, không có token cố định.
OpenAPI runtime `/v3/api-docs` là hợp đồng machine-readable; Swagger `/swagger-ui/index.html` cho phép thử trực tiếp.

## Toàn bộ 16 endpoint

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| POST | /api/auth/register | Công khai | 201, tài khoản USER |
| POST | /api/auth/login | Công khai | 200, accessToken/tokenType/expiresIn |
| POST | /api/auth/logout | Đăng nhập | 204, thu hồi token của tài khoản |
| GET | /api/auth/me | Đăng nhập | 200, danh tính để giao diện xác định quyền |
| GET | /api/auctions | Công khai | 200, danh sách phân trang |
| POST | /api/auctions | Đăng nhập | 201, phiên mới kèm món hàng |
| GET | /api/auctions/me | Đăng nhập | 200, các phiên của mình |
| GET | /api/auctions/{id} | Công khai | 200, chi tiết phiên |
| DELETE | /api/auctions/{id} | Chủ phiên, chưa có bid | 204, xóa phiên |
| POST | /api/auctions/{id}/bids | Đăng nhập | 201, bid tay |
| GET | /api/auctions/{id}/bids | Công khai | 200, lịch sử bid |
| GET | /api/users/me/bids | Đăng nhập | 200, bid của mình |
| GET | /api/admin/users | ADMIN | 200, danh sách tài khoản |
| DELETE | /api/admin/users/{id} | ADMIN | 204, xóa hẳn và tính lại phiên bị ảnh hưởng |
| GET | /api/admin/auctions | ADMIN | 200, danh sách phiên |
| DELETE | /api/admin/auctions/{id} | ADMIN | 204, xóa phiên và tất cả bid |

Các danh sách nhận `page` (từ 0), `size` (mặc định 20, tối đa 100), trả
`content`, `page`, `size`, `totalElements`, `totalPages`.
Danh sách phiên còn nhận `status` (SCHEDULED/ACTIVE/ENDED/FAILED) và `search` theo tên.
Không có endpoint sửa hồ sơ, sửa phiên, product, category, ví, thanh toán hay auto-bid.

## Ví dụ JSON

Đăng ký:

```json
{"username":"demo_user","email":"demo@example.com","password":"Demo123!"}
```

Đăng nhập:

```json
{"login":"demo_user","password":"Demo123!"}
```

Tạo phiên (thay thời gian tương lai khi thử):

```json
{
  "name":"Máy ảnh",
  "description":"Máy ảnh dùng thử cho phiên đấu giá",
  "condition":"USED",
  "imageUrl":null,
  "startingPrice":100000,
  "minimumBidStep":10000,
  "startingTime":"2030-01-01T09:00:00Z",
  "endingTime":"2030-01-01T10:00:00Z"
}
```

Condition là chuỗi mô tả tình trạng (ví dụ NEW/USED), tối đa 50 ký tự. Ảnh là một URL tùy chọn, không upload file. Thời gian là ISO-8601
có múi giờ; endingTime phải sau startingTime và thời điểm hiện tại. Để thử bid ngay,
đặt startingTime vừa qua, endingTime vài phút sau. Tiền tối đa 17 chữ số phần nguyên,
2 chữ số thập phân; tránh mất chính xác số lớn trên client, frontend dùng decimal.js/lossless-json.

Đặt giá:

```json
{"amount":100000}
```

Không kiểm tra số dư vì không có ví. Giá vẫn phải đáp ứng bước giá và trạng thái phiên.
Response phiên chứa thông tin món hàng, sellerId, giá, trạng thái, thời gian,
winningBidId/winnerUserId/finalPrice và timestamp. Không trả hash mật khẩu/tokenVersion.
Trạng thái bid: WINNING/OUTBID/WON/LOST; xem schema chính xác trong Swagger.

## Lỗi và kiểm thử nhanh

400: JSON/đầu vào/phân trang sai; 401: chưa đăng nhập, token hết hạn/thu hồi;
403: sai quyền hoặc bid phiên của mình; 404: không tồn tại;
409: trùng tài khoản, giá hoặc trạng thái không hợp lệ. Lỗi gồm timestamp, status,
error, message, path; kiểm tra response hiện tại trong OpenAPI/test. 500 là lỗi ngoài dự kiến,
không nên coi là xung đột bid bình thường.

Luồng thử: login seller → tạo phiên ACTIVE → login bidder1 đặt giá khởi điểm → bidder2
đặt cao hơn → GET chi tiết/lịch sử → chờ hết giờ và Làm mới → kiểm tra ENDED/người thắng.
Thử không token gọi `/api/auth/me` và POST phiên phải 401; USER gọi admin phải 403.
Thử hai bid đồng thời; giá/người dẫn đầu phải nhất quán. Admin xóa phiên có bid phải
xóa cả lịch sử; xóa bidder dẫn đầu phải tính lại theo bid còn lại.
Test tự động và demo trong README kiểm tra các luồng này.
