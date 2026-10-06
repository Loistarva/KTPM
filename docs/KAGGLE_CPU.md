# Bộ kiểm thử tải chung Pha 1 / Pha 2 — Kaggle CPU

## 1. Mục tiêu và phạm vi

Đo **hiệu năng, khả năng xử lý tải, sử dụng tài nguyên và tính đúng dữ liệu** của backend đấu giá. Giữ nguyên bộ đo cho hai pha để biết cải tiến có tác dụng ở đâu. Frontend không nằm trong phép đo; client gọi REST trực tiếp, bỏ qua độ trễ Internet.

Các file:

- `notebooks/KTPM_CPU_Benchmark.ipynb`: cài môi trường, build, chạy từng trial và xuất kết quả.
- `scripts/benchmark_config.json`: dữ liệu, mức tải, thời lượng và số lần lặp cố định.
- `scripts/benchmark_suite.py`: tạo dữ liệu bằng API, warmup, phát tải, kiểm tra dữ liệu sau đo.
- `scripts/compare_benchmarks.py`: kiểm tra khả năng so sánh rồi xuất CSV/JSON/Markdown P1–P2.
- `scripts/benchmark.py`: tiện ích dùng chung để đo latency/CPU/RAM; đã bỏ CLI read/bid/mixed cũ để chỉ còn một bộ đo.

Đây là kiểm thử tải, không thay thế toàn bộ kiểm thử nghiệp vụ/bảo mật. Đăng ký dùng để chuẩn bị tài khoản; logout và thao tác admin không có workload tải riêng. Không có kết quả Kaggle thực tế điền sẵn.

## 2. Các bộ kiểm thử

| Kịch bản | Dữ liệu/tải full | Mục đích |
|---|---|---|
| read_small | 100 phiên | Mốc đọc danh sách, tìm tên, lọc trạng thái, phân trang, chi tiết |
| read_large | 2.000 phiên | So sánh đọc khi dữ liệu lớn, kiểm tra ảnh hưởng truy vấn/index/cache |
| history_deep | 20 phiên, 1.000 bid ở một phiên | Lịch sử dài, phân trang bid |
| bid_hot | 1 phiên | Nhiều bidder tranh chấp cùng dữ liệu |
| bid_many | 32 phiên | Ghi phân tán, phân biệt nghẽn chung với khóa một phiên |
| create | Tạo 1.000 phiên/lượt | Tốc độ ghi và không mất/trùng dữ liệu |
| delete | Chuẩn bị 1.200 phiên, xóa 200 warmup + 1.000 đo | DELETE của chủ phiên, kiểm tra dữ liệu thực sự bị xóa |
| login | 50 bidder | Chi phí xác thực mật khẩu/JWT |
| authenticated_get | 50 bidder | GET `/api/auth/me`, kiểm tra đúng danh tính |
| mixed | 500 phiên | Luồng đọc/ghi gần tình huống sử dụng chung |
| deadline | 1 phiên, đóng sau 12 giây, tải 24 giây | Tranh chấp bid trước/sau thời điểm đóng; cấm nhận bid muộn |
| scheduler_small | 100 phiên, mở sau 20 giây, kết thúc sau 40 giây | Độ trễ mở/đóng và kết quả có/không có bid |
| scheduler_large | 500 phiên, cùng lịch | Scheduler khi nhiều phiên đến hạn cùng lúc |
| soak | 100 phiên, concurrency 25, 300 giây | RPS/latency/RAM biến động theo thời gian |
| ramp | 100 phiên, mức 1→10→25→50→100, 30 giây/mức | Tìm ngưỡng bão hòa, lỗi/latency tăng khi tải tăng |

Mười kịch bản đầu chạy concurrency **1/10/50**, deadline **10/50**, scheduler **1**, soak **25**; ramp tăng tải bên trong một trial. Mỗi tổ hợp lặp **3 lần**: tổng **108 trial/pha**, **216 trial** khi chạy cả hai pha.

Thông số chung full: 50 bidder + 1 chủ phiên, seed 42, warmup 200 request rồi đo 1.000 request (trừ các case chạy theo thời lượng hoặc scheduler). Dataset được seed ngoài thời gian đo. Mỗi trial dùng backend và database mới; database tạm được xóa sau khi tiến trình đã dừng để tránh đầy ổ đĩa Kaggle. Catalog có 1/4 phiên SCHEDULED để thử lọc; lịch sử dài được seed tuần tự để tạo bid hợp lệ.

Mixed/soak/ramp dùng tỷ lệ mục tiêu: danh sách 25%, chi tiết 20%, lịch sử 10%, bid 25%, tạo phiên 10%, phiên của tôi 5%, bid của tôi 5%. Lựa chọn pseudo-random theo seed và chỉ số request; tỷ lệ thực tế nằm trong report. Case theo thời lượng phát số request khác nhau tùy tốc độ backend — đó là kết quả cần đo.

Concurrency là số worker **closed-loop**: mỗi worker chờ response rồi gửi tiếp. 50 tài khoản không có nghĩa là 50 request/giây. Đây không phải phép đo tải open-loop theo tốc độ đến cố định; khi client cùng CPU bị bão hòa, RPS có thể phản ánh giới hạn client.

Smoke chỉ kiểm tra pipeline: 4 bidder, 24 request, warmup 4, concurrency 1/3, thời gian ngắn; tổng **25 trial/pha**. Không dùng smoke để kết luận cải tiến pha 2. Full có build/seed/audit và nhiều lượt, có thể mất hàng giờ; chạy khi còn đủ quota notebook.

## 3. Cách chạy Pha 1

1. Tại thư mục gốc dự án chạy PowerShell:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/export-kaggle.ps1
   ```

   File tạo ra: `backend/.local/kaggle/ktpm-p1-source.zip`. Nếu đã có, xóa **đúng ZIP cũ** trước khi xuất lại. Giữ một bản ZIP P1 đã đo làm baseline.
2. Trên Kaggle tạo Dataset, upload ZIP này. Tạo Notebook, import `notebooks/KTPM_CPU_Benchmark.ipynb`, Add Input chọn dataset.
3. Kaggle có thể tự giải nén ZIP thành `backend/` và `scripts/`. Notebook nhận cả ZIP lẫn thư mục đã giải nén; với một dataset source duy nhất sẽ tự tìm. Nếu có nhiều dataset, đặt đường dẫn source vào cell đầu:

   ```python
   PHASE_ARCHIVES = {'P1': '/kaggle/input/ten-dataset-cua-ban'}
   ```

   Thư mục cần chứa `backend/pom.xml` và `scripts/benchmark_suite.py` (có thể nằm trong một thư mục con). Notebook chép source sang `/kaggle/working` để build, không ghi vào Input. SHA256 source tính theo đường dẫn/nội dung file nên cùng source dạng ZIP hoặc giải nén cho cùng hash.
4. Chọn **Accelerator: None**, bật **Internet** cho bước cài/build. Không cần Docker trong Kaggle.
5. Để `PROFILE = 'smoke'`, `CASE_FILTER = []`, chạy tất cả cell. Kiểm tra mỗi report có `correctness.status = passed`.
6. Đổi `PROFILE = 'full'`, chạy lại **tất cả cell**. Notebook tự tạo folder kết quả mới, tránh ghép dữ liệu cũ.
7. Tải ZIP `results-full-<session>.zip` trong Output của notebook. Giữ notebook, ZIP source, config và kết quả cùng nhau.

Notebook tự cài Java nếu thiếu, build backend bằng Maven, tạo user thường khi kernel là root và chạy PostgreSQL thật thông qua `LocalDemo`. Backend, database và client cùng kernel Kaggle. Không liên quan dữ liệu Docker/portable trên máy Windows.

`CASE_FILTER = ['bid_hot']` có thể dùng khi sửa pipeline; báo cáo chính thức để `[]`. Không benchmark đồng thời một notebook khác. Nếu scheduler seed chưa xong trước giờ mở, runner dừng: tăng cùng offsets trong config của bộ đo rồi **đo lại cả hai pha**.

## 4. Cách so sánh Pha 2

1. Giữ nguyên ZIP P1 đã đo, đặc biệt runner và config bên trong.
2. Ở source P2 xuất ZIP tên khác:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/export-kaggle.ps1 -OutputPath backend/.local/kaggle/ktpm-p2-source.zip
   ```

3. Upload ZIP P2, thêm cả hai ZIP vào notebook. Trong cell cấu hình bật:

   ```python
   PROFILE = 'full'
   PHASE_ARCHIVES = {'P1': 'ktpm-p1-source.zip', 'P2': 'ktpm-p2-source.zip'}
   CASE_FILTER = []
   ```

Nếu Kaggle giải nén cả hai ZIP, đổi các giá trị sang hai thư mục dataset tương ứng, ví dụ:

```python
PHASE_ARCHIVES = {'P1': '/kaggle/input/dataset-p1', 'P2': '/kaggle/input/dataset-p2'}
```

4. Chạy lại tất cả cell. Notebook build cả hai source nhưng **luôn lấy harness/config từ P1**. Mỗi trial chạy P1/P2 xen kẽ, đảo thứ tự ở lần lặp chẵn; cả hai cùng kernel, heap JVM và database mới. `ActiveProcessorCount=2` là cấu hình JVM, không ghim hệ thống vào hai core vật lý; affinity và giới hạn cgroup thực tế được ghi riêng.
5. Mở `comparison/comparison.csv` hoặc `.md` trong ZIP kết quả. Có median/min/max ba lần đo và % cải thiện cho mỗi case/mức tải/metric. Ramp có dòng riêng cho từng mức tải; không chỉ xem số tổng.

Comparator từ chối khác session/phần cứng/cấu hình, khác harness/workload, database không mới, thiếu hoặc trùng trial/lần lặp. Trial correctness failed/indeterminate và smoke không được cấp % cải thiện chính thức. Không so kết quả P1 từ kernel cũ với P2 từ kernel mới: **đo lại P1 và P2 cùng phiên**.

Có thể chạy comparator sau khi giải nén kết quả:

```text
python scripts/compare_benchmarks.py --p1 <folder-P1> --p2 <folder-P2> --output <folder-so-sanh>
```

P2 phải giữ hợp đồng REST/nghiệp vụ được đo. Notebook hiện khởi chạy một backend bằng `com.ktpm.LocalDemo` cho mỗi pha. Nếu chuyển nhiều instance/Redis/worker riêng, cần sửa adapter triển khai, đo đủ PID và ghi topology mới; giữ tổng ngân sách CPU/RAM tương đương. Không cần thay bộ request và các kiểm tra dữ liệu. Không mặc định coi heap 512 MiB là giới hạn RAM toàn bộ hệ thống; PostgreSQL cũng dùng RAM và JVM còn bộ nhớ ngoài heap.

## 5. Các thông số và cách đọc

- **successfulRps**: request trả status đúng **và JSON đáp ứng kiểm tra** mỗi giây; khác totalRps.
- **p95/p99**: latency mà 95%/99% request không vượt quá; báo riêng tất cả request và request thành công. Số so sánh là median percentile từng trial, không phải percentile gộp.
- **HTTP/status, unexpectedErrorRate, authFailureRate**: timeout/network, JSON sai, 5xx, status ngoài kỳ vọng, 401/403 phải được đọc cùng tốc độ.
- **businessConflictRate**: tỷ lệ 409 của bid trên tổng request. 409 là tranh chấp hợp lệ và **không tính thành công**; tăng totalRps nhờ từ chối nhiều bid không chứng minh xử lý bid tốt hơn.
- **CPU seconds, CPU % một core, CPU ms/request thành công, peak RSS MiB, resource timeline**: chỉ cây tiến trình backend/PostgreSQL; client bị loại khỏi tổng này nhưng vẫn cạnh tranh CPU. CPU/request giúp đọc hiệu suất với case thời lượng cố định có số request khác nhau; gồm CPU của request lỗi/xung đột trong tổng nên vẫn phải đọc kèm 409/lỗi. RSS cộng có thể đếm trang dùng chung nhiều lần; sample 250 ms có thể bỏ lỡ peak/process rất ngắn.
- **Scheduler open/end lag p95/p99, missedOpen/missedEnd**: cận trễ quan sát qua API, có cả thời gian monitor/response; không phải timestamp nội bộ chính xác. Monitor có tải nền cố định, gồm đọc đồng thời tối đa 8 và nghỉ 500 ms giữa vòng.
- **Timeline mỗi 10 giây, từng operation, ramp từng mức**: xem tail latency, RPS và RAM có xấu dần không. RAM tăng ở một lần đo chưa đủ kết luận memory leak; phải xem lặp lại, heap/GC và tương quan workload.

% cải thiện throughput = `(P2 − P1) / P1 × 100`; latency/CPU/RAM = `(P1 − P2) / P1 × 100`. Số dương nghĩa là tốt hơn. Baseline bằng 0 hoặc không có số liệu thì để trống, không bịa tỷ lệ. Tỷ lệ lỗi/409 phải đọc giá trị tuyệt đối và nguyên nhân, không chỉ phần trăm.

## 6. Tính đúng và diễn giải cải tiến

Audit dùng REST, không phụ thuộc bảng SQL của P1. Kiểm tra bid đã trả thành công được lưu, số bid khớp, không trùng ID/giá sai bước, currentPrice/leading bid/người thắng/finalPrice đúng, chỉ một bid WINNING/WON và không có bid chủ phiên. Kiểm tra response tìm/lọc, danh tính GET xác thực, phiên tạo đúng chủ, phiên xóa không còn và tổng số phiên khớp.

Deadline phát bid liên tục qua giờ đóng; bid gửi sau endingTime mà nhận 201 sẽ làm audit fail. Scheduler còn thử bid sau giờ đóng, kiểm tra ENDED khi có bid, FAILED khi không có bid và người thắng/giá đúng. Timeout khi ghi có thể đã commit dù client chưa nhận xác nhận: đánh dấu không xác định, không coi là một lượt đo sạch.

Ví dụ hướng phân tích P2:

| Dấu hiệu P1 | Cải tiến có thể thử | Dùng case để chứng minh |
|---|---|---|
| Đọc dữ liệu lớn chậm hơn rõ | Index, sửa truy vấn, cache có quy tắc invalidation | read_small/read_large/history_deep + mixed để kiểm tra dữ liệu sau ghi |
| Một phiên nghẽn nhưng nhiều phiên tốt | Giảm đoạn giữ lock, tổ chức xử lý bid | bid_hot/bid_many + audit tính đúng |
| Trễ mở/đóng tăng theo số phiên | Scheduler xử lý batch, truy vấn theo thời hạn, worker | scheduler_small/scheduler_large/deadline |
| Latency/lỗi tăng khi tăng tải | Connection pool, backpressure, chia tải | ramp + mixed + từng endpoint |
| CPU đăng nhập cao | Điều chỉnh luồng xác thực, kiểm tra JWT | login/authenticated_get; giữ mức bảo mật tương đương |
| Tải lâu xuống cấp | Giảm allocation, quản lý connection/GC | soak + resource/request timeline |

Không chọn cải tiến trước rồi chỉ báo metric thuận lợi. Trình bày vấn đề P1, lý do thay đổi, điều kiện đo cố định, số liệu cả tốt và xấu, và đánh đổi. Ba lần lặp/min/max là đánh giá ban đầu, không tự chứng minh ý nghĩa thống kê; nếu nhiễu lớn phải tăng số lần cho **cả hai pha** và đo lại.
