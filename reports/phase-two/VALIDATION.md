# Kiểm chứng Pha 2 — 19/10/2026

Môi trường kiểm tra: Windows, JDK 21 (biên dịch Java 17), PostgreSQL portable 16.2, Chromium/Playwright. Database thử nghiệm tách riêng tại `backend/.local/phase-two-e2e-db`; các tiến trình thử nghiệm đã dừng sau khi hoàn tất.

| Nhóm | Kết quả |
|---|---|
| Maven verify + PostgreSQL thật | **42 test qua**, không bỏ qua; JAR đóng gói thành công |
| Frontend Vitest | **32 test qua** |
| Playwright | **9 kịch bản qua**, gồm SSE từ API replica khác origin |
| Công cụ benchmark Python | **20 test qua**, chạy với `PYTHONUTF8=1` trên Windows |
| TypeScript / Vite production build | Qua |
| Google Java Format / Prettier | Qua; Prettier dùng endOfLine auto để hỗ trợ checkout Windows |
| `git diff --check` | Qua |
| `docker compose config --quiet` | Qua |
| Prometheus thực tế | HTTP 200 với JWT ADMIN; có JVM memory metrics |

## Phạm vi đã chạy thực tế

Hai API độc lập trên 8080/8081 và worker độc lập trên 8082 dùng chung PostgreSQL. API không chạy scheduler/relay. Worker thực hiện mở/đóng phiên và phát outbox. Trình duyệt đọc REST qua API đầu; kịch bản liên instance mở EventSource trực tiếp qua API thứ hai và nhận được giá mới khi bid ở API đầu.

Các kịch bản trình duyệt gồm đăng ký/tạo phiên, bid và kết quả cuối tự cập nhật, xung đột giữ input mà không replay POST, xóa tài khoản tính lại kết quả/thu hồi token, xóa phiên/lịch sử, quyền seller/admin, phiên không có bid, tiền thập phân/double click, SSE liên instance, tìm kiếm/phân trang/hiển thị mobile.

Các test PostgreSQL bổ sung kiểm tra snapshot cũ không ghi đè người dẫn đầu, chờ khóa qua giờ đóng bị từ chối, hai worker claim batch khác nhau, SKIP LOCKED, hết lease và fencing token, retry/backoff, rollback không phát outbox, broadcast tới mọi LISTEN, relay không gửi lại hàng đã ACK và relay lỗi vẫn retry được. Các test đồng thời/nghiệp vụ/API/phân tầng cũ tiếp tục qua. Có test CORS/preflight và 503/Retry-After khi API/SSE hết sức chứa.

Đã xác nhận runtime có `spring.data.repository.invocations`, `jvm.memory.used`, `process.cpu.usage`, `hikaricp.connections.active` và `ktpm.db.lock.waiters`.

## Giới hạn của kết quả này

Docker Engine chưa chạy trên máy trong phiên kiểm tra, vì vậy chưa build/chạy toàn bộ container hoặc kiểm tra Nginx trong container. Topology API/worker/SSE đã được chạy bằng các tiến trình Java riêng; Compose đã qua kiểm tra cấu hình.

Chưa chạy lại benchmark Kaggle full P1/P2. Các test công cụ benchmark chỉ xác minh runner và cách đọc/so sánh dữ liệu, không phải bằng chứng tăng RPS hoặc giảm p95. Báo cáo Pha 1 được giữ nguyên. Cần đo lại hai archive baseline/current trong cùng môi trường trước khi công bố phần trăm cải thiện.

Chi tiết triển khai, cấu hình và quy trình đo lại: [Pha 2](../../docs/PHA_2.md).
