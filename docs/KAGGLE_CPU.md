# Chạy benchmark KTPM trên Kaggle CPU

Mục đích: đo baseline P1 rồi dùng cùng workload đo cải tiến P2. Notebook chạy
**backend + PostgreSQL + client tải trên cùng máy CPU Kaggle**, không gọi backend trên laptop.
Notebook chính thức: [notebooks/KTPM_Pha1_CPU.ipynb](../notebooks/KTPM_Pha1_CPU.ipynb).
Tham khảo nền tảng: [Kaggle notebooks](https://www.kaggle.com/docs/notebooks),
[image Kaggle CPU](https://github.com/Kaggle/docker-python).

## 1. Xuất source ở Windows

Tại thư mục gốc KTPM:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/export-kaggle.ps1
```

File tạo tại `backend/.local/kaggle/ktpm-p1-source.zip`. Gói chỉ gồm source backend,
Maven wrapper và runner benchmark; không chứa database demo, token, .env, .git,
node_modules hoặc Maven cache. Không cần commit/push để tạo gói này.
Nếu file đã tồn tại, xóa đúng ZIP cũ hoặc truyền `-OutputPath` khác trước khi xuất lại.

## 2. Tạo notebook CPU

Đăng nhập Kaggle → tạo Dataset riêng, upload ZIP trên → tạo Notebook → Import Notebook
và chọn `KTPM_Pha1_CPU.ipynb` → Add Input dataset vừa tạo.
Trong Settings chọn Accelerator **None**, bật Internet. Internet dùng tải Maven,
dependency Java và PostgreSQL binary. Không cần Docker, DBeaver hoặc frontend trên Kaggle.
Nếu tìm thấy nhiều ZIP trùng tên, chỉnh SOURCE_ZIP trong cell đầu về đúng đường dẫn.

## 3. Chạy smoke trước

Giữ `SMOKE = True`, chạy lần lượt mọi cell. Smoke gồm 3 mode × concurrency 1/3 ×
1 lần, mỗi trial 30 request. Đây chỉ là kiểm tra pipeline, không phải số liệu nộp.
Notebook tự build Java, chạy PostgreSQL dưới user thường (initdb không chạy root),
đợi health, tạo user/phiên, warmup, đo, dừng đúng tiến trình của trial.

Nếu Java/dependency không tải được, kiểm tra Internet và log build. Nếu backend không
lên, đọc `/kaggle/working/ktpm-benchmark/runtime-.../backend.log`.
Nếu trial cũ còn chạy, dừng/restart kernel; notebook từ chối xóa DB đang có tiến trình sở hữu.
Notebook cần Linux CPU image; chưa được thực thi trên Kaggle trong lần sửa này.

## 4. Đo chính thức

Đổi `SMOKE = False`, chạy lại cell cấu hình, môi trường, hàm trial và cell đo.
Mặc định 3 mode × concurrency 1/10/50 × 3 lần = **27 trial**; 1.000 request/trial.
Mỗi trial database mới, 25 bidder, một phiên ACTIVE 50 phút; warmup 100 request.
JVM `-Xms256m -Xmx512m -XX:ActiveProcessorCount=2`, PostgreSQL 16.2, seed 42.
Giữ nguyên thông số khi so sánh. Không tính setup/build/warmup vào elapsed của phép đo.

| Mode | Workload | Điều quan sát |
|---|---|---|
| read | Luân phiên list/detail/history | Thời gian đọc, phân trang |
| bid | Nhiều bidder vào cùng một phiên | Khóa hàng, 409, bid thành công |
| mixed | 70% list, 15% detail, 15% bid theo seed | Cạnh tranh đọc/ghi |

Bid gán giá tăng theo thứ tự request được tạo; thứ tự đến server có thể đảo nên 409
là bình thường. Không cố loại bỏ 409 để làm đẹp số đo. Request lớn hơn mới thắng
nên dữ liệu cuối có thể khác giữa trial: lưu raw samples và trình bày biến động.

## 5. Đọc/tải kết quả

Output: `/kaggle/working/ktpm-benchmark/results-p1.zip` (smoke: results-smoke.zip).
Tải qua Output/Files của notebook hoặc Save Version chạy toàn bộ cell rồi tải output.
ZIP chỉ chứa environment.json, báo cáo từng trial, raw JSONL và summary.csv; không fixture token.

Mỗi trial ghi tổng RPS và **successful RPS (2xx)**, p95/p99 cho toàn bộ request và
request thành công, histogram HTTP, businessConflictRate (409), unexpectedErrorRate
(network/HTTP lỗi khác 409), authFailureRate (401/403), CPU seconds/% một core và peak RSS.
Không có request thành công thì latencySuccessful là null, không phải 0.

CPU/RSS lấy bằng [psutil](https://psutil.readthedocs.io/stable/index.html), gồm backend
và cây PostgreSQL, loại client khỏi bộ đếm nhưng client vẫn dùng cùng CPU. RSS cộng
có thể đếm trùng shared pages; chu kỳ 250ms có thể bỏ lỡ tiến trình ngắn. CPU tổng
không tách query/lock wait. RPS là closed-loop với số request cố định; không chứng minh
SLA hay tải người dùng thực trên Internet. summary.csv là median số đo trial, không p95 gộp.

## 6. Báo cáo P1 và so sánh P2

Lưu ZIP source P1, SHA256, notebook version và kết quả trước cải tiến. Ghi CPU/RAM,
affinity, Java/PostgreSQL/Python, JVM, mode/concurrency/request/warmup/users/seed, topology.
Chọn cải tiến từ bottleneck quan sát được; giải thích thuộc tính chất lượng muốn cải thiện.

Đo P1/P2 trong cùng kernel/cấu hình; nếu phần cứng đổi, đo lại cả hai. Dùng runner P1
giữ SHA256, database đầu vào tương đương và thông số y hệt. Notebook có chỉ dẫn đổi
source/nhãn output cho P2. Khi thay topology ghi tổng giới hạn CPU/RAM; không âm thầm
cấp thêm tài nguyên rồi kết luận kiến trúc tốt hơn. So sánh median 3 lần kèm độ biến động,
p95/p99, successful RPS, 409, lỗi và tài nguyên; kiểm tra chức năng vẫn đúng.

Không đưa số đo smoke local hoặc kết quả giả vào báo cáo Kaggle. Bộ chạy đã chuẩn bị,
**chưa có kết quả Kaggle thật** cho đến khi bạn chạy notebook bằng tài khoản của mình.

## Chạy runner local để kiểm tra nhanh

Backend demo phải đang chạy; Python 3.10+:

```powershell
python scripts/test_benchmark.py
python scripts/benchmark.py prepare --fixture backend/.local/benchmark-fixture.json
python scripts/benchmark.py run --fixture backend/.local/benchmark-fixture.json --mode read --requests 30 --concurrency 3 --output backend/.local/read.json --samples-output backend/.local/read.jsonl
```

Lặp mode bid/mixed; thêm `--server-pid` và `--database-pid` nếu đã cài psutil và biết
đúng PID. Thiếu PID, metric tài nguyên báo unavailable; không tự suy đoán CPU từ latency.
Local chuẩn bị thêm dữ liệu thử nên chỉ dùng database demo. Xóa fixture token sau khi dùng.
