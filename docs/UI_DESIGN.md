# Giao diện KTPM Auction

Giao diện dùng nền kem, xanh rừng và điểm nhấn lime, kết hợp tiêu đề sans-serif và dòng serif nghiêng trên banner. Màu chính `#244c3b`, nền `#fafaf7`, accent `#d5e9a1`. Font chính Be Vietnam Pro, có fallback system sans-serif.

## Các màn hình

- Trang chủ có banner sản phẩm, giới thiệu cách tham gia, tìm kiếm, lọc trạng thái, trạng thái trống có thao tác tiếp theo và lời mời tạo phiên.
- Thẻ phiên có ảnh/placeholder trung thực, trạng thái, mức giá và đồng hồ đếm ngược.
- Chi tiết phiên tách vùng sản phẩm và vùng đặt giá; có thời gian còn lại, người dẫn đầu, thông tin mức giá/thời gian và lịch sử. Nút gợi ý chỉ điền giá; người dùng vẫn bấm Đặt giá để gửi.
- Đăng nhập/đăng ký dùng bố cục hai cột trên desktop, form gọn trên điện thoại. Không thêm chức năng khôi phục mật khẩu hay đăng nhập ngoài khi API chưa có.
- Form tạo phiên nhóm thông tin món hàng và giá/thời gian. Trang quản trị có tổng số tài khoản/phiên lấy từ API.
- Giao diện có focus bàn phím, liên kết bỏ qua navigation, reduced motion, mobile menu, skeleton khi tải và thông báo trạng thái realtime.

Số phiên và lịch sử đều lấy từ API; ảnh banner chỉ là hình minh họa. Không thêm dữ liệu phiên giả vào database người dùng.

## Ảnh banner

Asset được lưu trong project tại `frontend/public/images/auction-editorial.webp` (1536 × 1024, khoảng 159 KB). Tạo bằng **image_gen built-in**, rồi đổi mã hóa sang WebP để giảm dung lượng. Bản PNG gốc được giữ tại thư mục generated_images của Codex.

Prompt cuối cùng:

```text
Use case: product-mockup. Asset type: editorial hero photograph for a premium Vietnamese auction marketplace website. Create an exceptionally refined, photorealistic still life of a silver vintage rangefinder camera with a black lens, a minimal silver wristwatch with a dark olive leather strap, and warm ivory over-ear headphones, carefully arranged on two low sculptural travertine plinths. Color palette: warm ivory, deep forest green, muted olive, brushed silver, a restrained chartreuse accent. Backdrop: seamless pale sage-olive studio wall and warm ivory stone floor, subtle sunlight from upper left casting beautiful long soft shadows. Composition: landscape 3:2 photograph, objects clustered around the center and slightly right, camera largest at rear on tall plinth, watch angled naturally across front plinth, headphones resting to the right. Show tactile metal, glass, leather and stone textures. Quiet sophisticated design magazine aesthetic, real studio product photography, natural proportions, crisp premium materials, medium depth of field. Leave a little breathing room around all edges. No people, no hands, no text, no lettering, no numbers, no logos, no brands, no watermarks. The image will be placed in the right half of a website banner; no interface or typography rendered in the image.
```

## Kiểm tra

Build bằng `npm.cmd run build`; kiểm tra định dạng bằng `npm.cmd run format:check`; unit test bằng `npm.cmd test` trong frontend. Các kịch bản nghiệp vụ hiện có nằm trong `frontend/e2e/full.spec.ts`.

Đã dùng môi trường PostgreSQL/API/Vite riêng cho QA giao diện, để dữ liệu thử nghiệm không lẫn với database đang dùng. Kiểm tra bố cục desktop 1440 px và mobile 390/320/300 px, ảnh tải thành công và không tràn ngang; kiểm tra lại các luồng đăng nhập/tạo phiên/bid/SSE/kết quả bằng Playwright.

Kết quả: 32 unit test qua; 8 kịch bản E2E nghiệp vụ và một lượt QA responsive qua. Kịch bản SSE qua replica được bỏ qua trong lượt UI này vì môi trường kiểm thử dùng một backend; kiểm chứng nhiều instance trước đó nằm trong báo cáo Pha 2. Build production và kiểm tra định dạng thành công.

## Cải tiến UX ngày 27/10/2026

Người dùng chọn ưu tiên luồng **tìm phiên và đặt giá**. Lựa chọn thiết kế của Codex là tiếp tục tông xanh–kem và cải thiện các bước đang có, phù hợp với chức năng API hiện tại. Các lựa chọn khác là chỉ đổi phong cách hình ảnh hoặc mở rộng luồng đăng bán; chúng chưa phải trọng tâm của lượt này.

- Rút gọn nội dung banner. Trên điện thoại, ẩn ảnh trang trí và các khối giới thiệu phụ để người dùng tiếp cận ô tìm kiếm sớm hơn; desktop tiếp tục dùng ảnh gốc.
- Đồng bộ từ khóa khi quay lại/tiến tới trong trình duyệt. Có nút xóa từ khóa, từng bộ lọc và toàn bộ bộ lọc, kể cả khi danh sách vẫn có kết quả. Trạng thái kết nối được lấy từ luồng realtime.
- Tăng cỡ chữ của nội dung, biểu mẫu và bộ lọc; input trên điện thoại dùng 16 px. Nút thao tác chính có vùng bấm tối thiểu 44 px.
- Chi tiết phiên có liên kết đến phần đặt giá, thông tin món hàng và lịch sử. Trên mobile, phần đặt giá đứng trước mô tả và có thanh lối tắt ở đáy màn hình cho phiên đang diễn ra.
- Khi nhập mức giá hợp lệ, hiển thị số tiền chuẩn bị gửi theo định dạng tiền Việt Nam. Mức thấp hơn tối thiểu có nhắc nhở riêng. Nút gợi ý không gửi request; chỉ nút Đặt giá gửi bid. Số tiền nhập được xóa khi chuyển sang một phiên khác.
- Trường nhập liên kết nhãn, hướng dẫn và lỗi bằng thuộc tính hỗ trợ đọc màn hình. Form đăng nhập/đăng ký có hiện/ẩn mật khẩu. Menu mobile đóng bằng Escape và trả focus về nút mở menu.

Không thay đổi luật đấu giá, kiểu số tiền hoặc cơ chế gửi lại POST. Test sử dụng database riêng trên 8090 và frontend 5174; bản xem trước thông thường tiếp tục dùng 8080/5173 với dữ liệu hiện có.

Kiểm thử hồi quy mới ở `frontend/tests/ux.test.tsx` và `frontend/e2e/ux.spec.ts`. Các kiểm tra bố cục bao gồm desktop/tablet và mobile 390/320/300 px. Không có thay đổi backend trong lượt cải tiến này.

Kết quả cuối: **41 unit test qua**, **9 kịch bản nghiệp vụ E2E và 1 lượt QA responsive qua**. Một kịch bản SSE qua API replica được bỏ qua do môi trường thử nghiệm chỉ có một API. Build production, TypeScript và Prettier qua. Đã kiểm tra thêm chiều rộng 760/768/1024/1440 px và từ khóa 200 ký tự trên mobile, không tràn ngang. Hai ca hồi quy bổ sung xác nhận số tiền dán có khoảng trắng ở đầu/cuối vẫn xem trước và gửi đúng một request với số tiền đã chuẩn hóa; không làm lỗi giao diện.
