# Hệ thống Đấu giá Cơ bản (Auction System) - Backend Service

Dự án phát triển dịch vụ Backend cho hệ thống đấu giá trực tuyến, đáp ứng các tiêu chuẩn kiến trúc phần mềm phân tầng (Clean Architecture) và bảo mật cơ bản.

## 1. Kiến trúc hệ thống

Dự án áp dụng chặt chẽ **Kiến trúc phân tầng (Clean Architecture / Hexagonal Architecture)**, chia làm 3 module chính để đảm bảo tính độc lập của nghiệp vụ:

*   **Tầng Web/API (`api`):** Giao tiếp REST API, nhận HTTP request, phân tích payload JSON qua DTO, xử lý middleware xác thực bảo mật và chuyển tiếp gọi các Service tương ứng.
*   **Tầng Nghiệp vụ (`domain`):** Chứa các POJO Domain Models (`Auction`, `User`, `Bid`) và logic nghiệp vụ lõi (ví dụ: giá thầu đưa ra phải cao hơn giá cao nhất hiện tại). **Tầng này sử dụng Java nguyên bản, hoàn toàn không phụ thuộc hay import các thư viện framework web (Spring) hoặc thư viện database (JPA/Hibernate).** Giao tiếp với các tầng bên dưới thông qua Interfaces (Dependency Inversion).
*   **Tầng Truy cập dữ liệu (`infrastructure`):** Implement các Interface kho chứa (Repository) từ tầng Domain. Cấu hình Spring Data JPA (Hibernate ORM) để map các Entity và truy xuất cơ sở dữ liệu H2. Tầng này chịu trách nhiệm chuyển đổi (Data Mapping) giữa Domain Model và Database Entity.

## 2. Đặc tả API (OpenAPI/Swagger)

Hệ thống giao tiếp hoàn toàn thông qua định dạng JSON. Tài liệu đặc tả API tương tác trực tiếp được tự động sinh bằng thư viện Springdoc OpenAPI.

*   **Đường dẫn Swagger UI:** `http://localhost:8080/swagger-ui.html`

| Method | Endpoint | Yêu cầu xác thực | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/auctions` | Không | Lấy danh sách tổng hợp các phiên đấu giá. |
| **GET** | `/api/auctions/{id}` | **Có (JWT)** | Lấy thông tin chi tiết của một phiên đấu giá cụ thể. |
| **POST** | `/api/auctions/{id}/bids` | **Có (JWT)** | Thực hiện đặt giá thầu cho một sản phẩm (Payload JSON). |
| **DELETE** | `/api/auctions/{id}` | **Có (JWT)** | Xóa phiên đấu giá (Chỉ dành cho chủ sở hữu). |

## 3. Cơ chế Bảo mật & Xác thực

Hệ thống sử dụng **JSON Web Token (JWT)** thông qua Header `Authorization: Bearer <token>`.

*   **Xác thực tập trung qua Middleware:** Thay vì kiểm tra lặp lại ở từng endpoint, hệ thống sử dụng một Filter bảo mật tùy chỉnh (`JwtAuthenticationFilter` kế thừa `OncePerRequestFilter`). Mọi HTTP request đi vào hệ thống (trừ danh sách whitelist cấu hình trong `SecurityConfig`) đều bị chặn lại để bóc tách và xác minh chữ ký token. Nếu token hợp lệ, Filter mới cấp quyền đi tiếp vào Controller.
*   **Quản trị dữ liệu:** Giao diện H2 Console được cấp quyền truy cập riêng biệt và mở khóa cấu hình Iframe (X-Frame-Options) phục vụ mục đích kiểm tra CSDL cục bộ.

## 4. Hướng dẫn Triển khai & Khởi chạy

### Yêu cầu môi trường chuẩn bị (Prerequisites)
* Java Development Kit (JDK) 17 trở lên.
* Docker Desktop (nếu chọn triển khai qua Docker).

### Cách 1: Chạy trực tiếp (Môi trường Local)
1. Mở Terminal (PowerShell hoặc Command Prompt) ngay tại thư mục gốc của dự án, nơi chứa file pom.xml.
2. Thực thi lệnh sau để hệ thống tự động dọn dẹp bộ nhớ đệm, tải thư viện và chạy ứng dụng:
   mvnw.cmd clean spring-boot:run
   (Lưu ý: Nếu chạy trên macOS/Linux, sử dụng lệnh ./mvnw clean spring-boot:run)
3. Đợi tiến trình khởi động đến khi Terminal xuất hiện dòng báo Tomcat đã start thành công.
4. Xác minh hệ thống hoạt động:
   * Tài liệu API (Swagger): Mở trình duyệt truy cập http://localhost:8080/swagger-ui.html
   * Cơ sở dữ liệu H2: Truy cập http://localhost:8080/h2-console
     * JDBC URL: jdbc:h2:mem:auctiondb
     * User Name: sa
     * Password: (để trống)

### Cách 2: Đóng gói và Triển khai qua Docker
Quy trình này sử dụng file Dockerfile đã được cấu hình Multi-stage build (tách biệt môi trường build mã nguồn và môi trường chạy app để tối ưu dung lượng).

1. Đảm bảo ứng dụng Docker Desktop đang chạy. Mở Terminal tại thư mục gốc dự án.
2. Đóng gói mã nguồn thành Docker Image có tên auction-backend (lưu ý có dấu chấm . ở cuối lệnh):
   docker build -t auction-backend .
3. Khởi chạy Container từ Image vừa tạo. Lệnh này sẽ ánh xạ cổng 8080 và chạy ngầm:
   docker run -d -p 8080:8080 --name auction-app auction-backend
4. Xác minh hệ thống bằng cách truy cập các đường dẫn ở Cách 1.
5. Khi cần dừng hệ thống, thực thi lệnh:
   docker stop auction-app


## 5. Kiểm thử tải (Load Testing) trên Kaggle CPU

Dự án trải qua quá trình kiểm thử chịu tải để xác nhận độ bền bỉ của REST API cơ bản.
*   **Phương pháp:** Cấu hình đường hầm (tunnel) qua Ngrok kết nối môi trường local với Public Internet. Khởi chạy kịch bản đa luồng (Multi-threading) bằng Python Script (sử dụng `requests` và `concurrent.futures`) trên nền tảng CPU của Kaggle Notebook.
*   **Mục tiêu:** Bắn luồng traffic liên tục vào các endpoint public (GET) và private (POST) để đánh giá tỷ lệ phản hồi HTTP 200/400/401 và khả năng xử lý đồng thời của Web Server (Tomcat) cùng Database (H2).
