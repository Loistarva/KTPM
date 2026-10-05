# Kiến trúc tổng quan KTPM

```mermaid
flowchart TB
    Browser["Người dùng / Admin<br/>Trình duyệt"]
    Swagger["Swagger<br/>Thử REST API"]

    subgraph Frontend["Container frontend — cổng 5173"]
        UI["React + TypeScript<br/>Giao diện đấu giá"]
        Nginx["Nginx<br/>Phục vụ giao diện và proxy /api"]
        Nginx -->|"File HTML / CSS / JS"| UI
    end

    subgraph Backend["Container backend — cổng 8080"]
        Security["Spring Security + JWT filter<br/>Xác thực và phân quyền"]
        API["API — Controller<br/>Nhận JSON, validation, trả HTTP"]
        Application["Application — Nghiệp vụ<br/>Auth · User · Auction · Bidding · Admin"]
        Domain["Domain / interface thuần Java<br/>Model, luật bid, Store và UnitOfWork"]
        Infrastructure["Infrastructure<br/>Adapter JPA, ORM và transaction"]
        Scheduler["Scheduler trong auction<br/>Quét và mở/kết thúc phiên"]

        Security --> API --> Application
        Application --> Domain
        Domain -->|"Gọi adapter triển khai interface"| Infrastructure
        Scheduler -->|"Gọi nghiệp vụ theo lịch"| Application
    end

    subgraph Database["Container PostgreSQL — cổng 5432"]
        DB[("users · auctions · bids<br/>flyway_schema_history")]
        Volume["Docker volume<br/>Lưu dữ liệu qua các lần khởi động"]
        DB --- Volume
    end

    Browser -->|"Mở trang"| Nginx
    Browser -->|"Thao tác trên giao diện"| UI
    UI -->|"REST / JSON, JWT khi cần"| Nginx
    Nginx -->|"Proxy /api tới backend:8080"| Security
    Swagger -->|"REST / JSON"| Security
    Infrastructure -->|"Đọc/ghi PostgreSQL"| DB
```

- API gọi nghiệp vụ; nghiệp vụ dùng model và interface, không import framework web/DB. JPA và transaction được triển khai ở infrastructure. `UnitOfWork` nằm trong common.
- JWT filter xử lý xác thực tập trung; endpoint công khai không yêu cầu token, endpoint admin cần quyền ADMIN.
- Scheduler xử lý phiên đến hạn, chờ 5 giây sau mỗi lượt. Giao diện lấy dữ liệu khi thao tác hoặc Làm mới/F5, không có realtime/polling.
- Sơ đồ thể hiện cách chạy Docker. Khi chạy trực tiếp, React dùng Vite proxy, backend và PostgreSQL portable chạy trên host; các tầng backend giữ nguyên.
