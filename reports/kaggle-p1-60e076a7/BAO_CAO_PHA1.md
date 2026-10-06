# Báo cáo kiểm thử tải Pha 1 — KTPM

## 1. Kết quả tổng quát

Nguồn: `results-full-60e076a7.zip`; session `60e076a7186a4fbe9b303355deeb1588`. Dữ liệu chỉ có P1; chưa có phép so sánh cải tiến P2.
Đủ **108 lượt / 15 kịch bản**, mỗi tổ hợp lặp 3 lần; **108/108 audit báo passed**. Đối chiếu ma trận, môi trường, hash cấu hình và raw samples: **0 sai lệch**.
Có **853,846 request đo**, **797,797 thành công**, **56,049 xung đột bid HTTP 409**, **0 lỗi ngoài dự kiến**. Không bao gồm setup/warmup, request audit và monitor scheduler. Không lấy trung bình RPS của mọi kịch bản làm năng lực hệ thống.

## 2. Môi trường và phương pháp

| Thành phần | Cấu hình ghi nhận |
|---|---|
| CPU | Intel Xeon 2.20 GHz; 4 CPU logic; affinity 0–3; quota cgroup 4 CPU |
| RAM | Host 31.35 GiB; giới hạn cgroup 30 GiB |
| Java / PostgreSQL | OpenJDK 21.0.12 / PostgreSQL 16.2 |
| JVM | -Xms256m -Xmx512m -XX:ActiveProcessorCount=2 |
| Python / psutil | 3.13.15 / 5.9.5 |
| Topology | Một backend, PostgreSQL và client closed-loop cùng kernel Kaggle |
| Dữ liệu / warmup | 50 bidder + 1 seller; seed 42; warmup 200; thường 1.000 request đo/lượt |

Soak đo 300 giây; ramp 5 mức × 30 giây; deadline 24 giây. Scheduler đo thời điểm quan sát mở/đóng. Database/backend mới mỗi trial. ActiveProcessorCount là cấu hình JVM, không ghim toàn hệ thống vào 2 core. CPU/RSS chỉ tính cây backend + PostgreSQL, client vẫn cạnh tranh CPU cùng kernel.

## 3. Hiệu năng theo kịch bản

Các số dưới đây là **median qua 3 trial**. p95/p99 là median percentile từng trial, không phải percentile gộp. Latency trong bảng là request thành công; số liệu tất cả request và min/max có trong `summary.json`. Tỷ lệ 409 tính trên tổng request đo của từng trial.

| Kịch bản | Worker | RPS tổng | RPS thành công | p95 ms | p99 ms | 409 % | Lỗi % |
|---|---:|---:|---:|---:|---:|---:|---:|
| authenticated_get | 1 | 162.57 | 162.57 | 7.85 | 9.08 | 0.00 | 0.00 |
| authenticated_get | 10 | 423.06 | 423.06 | 36.03 | 44.77 | 0.00 | 0.00 |
| authenticated_get | 50 | 420.64 | 420.64 | 215.98 | 271.06 | 0.00 | 0.00 |
| bid_hot | 1 | 89.71 | 89.71 | 14.73 | 15.98 | 0.00 | 0.00 |
| bid_hot | 10 | 212.78 | 86.53 | 60.13 | 65.93 | 58.80 | 0.00 |
| bid_hot | 50 | 249.45 | 29.93 | 135.21 | 165.77 | 88.00 | 0.00 |
| bid_many | 1 | 92.05 | 92.05 | 13.87 | 16.02 | 0.00 | 0.00 |
| bid_many | 10 | 263.16 | 263.16 | 55.43 | 63.57 | 0.00 | 0.00 |
| bid_many | 50 | 258.52 | 211.93 | 288.18 | 355.16 | 18.60 | 0.00 |
| create | 1 | 113.48 | 113.48 | 11.56 | 13.12 | 0.00 | 0.00 |
| create | 10 | 302.81 | 302.81 | 46.55 | 54.98 | 0.00 | 0.00 |
| create | 50 | 314.35 | 314.35 | 285.65 | 353.97 | 0.00 | 0.00 |
| deadline | 10 | 312.53 | 35.06 | 65.62 | 82.67 | 88.78 | 0.00 |
| deadline | 50 | 342.98 | 12.59 | 145.98 | 186.30 | 96.43 | 0.00 |
| delete | 1 | 135.29 | 135.29 | 8.88 | 9.84 | 0.00 | 0.00 |
| delete | 10 | 408.72 | 408.72 | 37.75 | 53.77 | 0.00 | 0.00 |
| delete | 50 | 422.13 | 422.13 | 217.20 | 290.44 | 0.00 | 0.00 |
| history_deep | 1 | 118.26 | 118.26 | 10.13 | 11.32 | 0.00 | 0.00 |
| history_deep | 10 | 341.33 | 341.33 | 44.49 | 65.05 | 0.00 | 0.00 |
| history_deep | 50 | 365.66 | 365.66 | 263.63 | 355.36 | 0.00 | 0.00 |
| login | 1 | 10.67 | 10.67 | 96.24 | 107.33 | 0.00 | 0.00 |
| login | 10 | 31.24 | 31.24 | 382.76 | 419.29 | 0.00 | 0.00 |
| login | 50 | 30.97 | 30.97 | 2,783.32 | 2,943.39 | 0.00 | 0.00 |
| mixed | 1 | 111.95 | 111.95 | 13.70 | 15.85 | 0.00 | 0.00 |
| mixed | 10 | 276.81 | 276.81 | 57.63 | 76.99 | 0.00 | 0.00 |
| mixed | 50 | 302.47 | 296.12 | 305.89 | 386.83 | 2.60 | 0.00 |
| read_large | 1 | 105.17 | 105.17 | 14.37 | 16.19 | 0.00 | 0.00 |
| read_large | 10 | 291.62 | 291.62 | 54.75 | 67.39 | 0.00 | 0.00 |
| read_large | 50 | 300.59 | 300.59 | 322.14 | 439.37 | 0.00 | 0.00 |
| read_small | 1 | 122.98 | 122.98 | 11.60 | 13.98 | 0.00 | 0.00 |
| read_small | 10 | 295.60 | 295.60 | 52.40 | 65.29 | 0.00 | 0.00 |
| read_small | 50 | 308.44 | 308.44 | 302.01 | 391.56 | 0.00 | 0.00 |
| soak | 25 | 540.90 | 538.43 | 84.30 | 110.17 | 0.46 | 0.00 |

## 4. Ramp: tăng tải trong cùng backend

| Worker | RPS thành công median [min–max] | p95 ms | p99 ms | 409 % |
|---|---:|---:|---:|---:|
| 1 | 132.70 [129.51–139.58] | 11.70 | 15.20 | 0.00 |
| 10 | 517.75 [505.26–521.62] | 31.94 | 42.95 | 0.00 |
| 25 | 574.58 [558.49–578.24] | 74.24 | 93.54 | 0.49 |
| 50 | 634.65 [597.11–649.62] | 139.05 | 173.85 | 2.29 |
| 100 | 617.97 [609.12–619.88] | 290.41 | 378.88 | 5.83 |

RPS thành công tăng từ khoảng 133 lên 635 khi tăng 1→50 worker; ở 100 worker khoảng 618, p95 tăng từ 139 lên 290 ms. Đây là dấu hiệu bắt đầu bão hòa trong workload này, chưa chứng minh riêng backend hoặc database là nguyên nhân. Ramp giữ cùng tiến trình và dữ liệu tăng theo thời gian; có cả hiệu ứng warmup/JIT và thứ tự stage.

## 5. Scheduler

| Kịch bản | p95 mở ms median [min–max] | p95 đóng ms median [min–max] | Bỏ lỡ mở/đóng |
|---|---:|---:|---|
| scheduler_small | 1,216.78 [831.60–1,464.86] | 1,741.72 [1,191.01–1,947.06] | 0 / 0 ở cả 3 trial |
| scheduler_large | 1,960.34 [1,424.86–2,341.85] | 4,506.19 [4,013.26–4,902.88] | 0 / 0 ở cả 3 trial |

Đóng 500 phiên có p95 quan sát khoảng 4,51 giây, so với 1,74 giây cho 100 phiên. Độ trễ này gồm quét fixed-delay, xử lý tuần tự và monitor API; không phải timestamp chuyển trạng thái nội bộ. Scheduler không bỏ lỡ phiên theo giới hạn kiểm thử. Deadline không ghi nhận bid gửi sau giờ kết thúc được chấp nhận; 409 cao ở deadline còn do thiết kế tải tiếp tục sau giờ đóng, không được diễn giải toàn bộ là tranh chấp khóa.

## 6. CPU và bộ nhớ

Median CPU/RSS theo tổ hợp; CPU % tính theo một core, vì vậy có thể lớn hơn 100%. RSS là tổng cây tiến trình, có thể đếm trang dùng chung nhiều lần.

| Kịch bản | Worker | CPU giây | CPU % một core | CPU ms/request thành công | Peak RSS MiB |
|---|---:|---:|---:|---:|---:|
| authenticated_get | 1 | 11.93 | 189.41 | 11.93 | 777.65 |
| authenticated_get | 10 | 8.09 | 339.69 | 8.09 | 775.07 |
| authenticated_get | 50 | 8.07 | 337.22 | 8.07 | 780.38 |
| bid_hot | 1 | 21.07 | 186.69 | 21.07 | 812.25 |
| bid_hot | 10 | 15.72 | 334.00 | 38.44 | 826.45 |
| bid_hot | 50 | 13.45 | 334.07 | 112.08 | 834.58 |
| bid_many | 1 | 20.57 | 189.25 | 20.57 | 816.14 |
| bid_many | 10 | 13.69 | 357.27 | 13.69 | 827.16 |
| bid_many | 50 | 13.84 | 359.36 | 17.05 | 832.67 |
| create | 1 | 17.06 | 191.03 | 17.06 | 807.76 |
| create | 10 | 11.70 | 354.48 | 11.70 | 807.79 |
| create | 50 | 11.28 | 352.75 | 11.28 | 810.93 |
| deadline | 10 | 78.81 | 327.54 | 93.60 | 842.97 |
| deadline | 50 | 79.47 | 329.03 | 261.45 | 849.21 |
| delete | 1 | 14.99 | 203.04 | 14.99 | 818.43 |
| delete | 10 | 8.37 | 340.54 | 8.37 | 820.84 |
| delete | 50 | 8.11 | 339.67 | 8.11 | 828.79 |
| history_deep | 1 | 15.17 | 179.28 | 15.17 | 832.41 |
| history_deep | 10 | 10.14 | 342.05 | 10.14 | 829.27 |
| history_deep | 50 | 9.47 | 336.97 | 9.47 | 836.14 |
| login | 1 | 100.94 | 107.35 | 100.94 | 783.43 |
| login | 10 | 124.46 | 388.87 | 124.46 | 785.92 |
| login | 50 | 125.63 | 388.51 | 125.63 | 793.21 |
| mixed | 1 | 18.58 | 207.80 | 18.58 | 825.53 |
| mixed | 10 | 12.56 | 347.03 | 12.56 | 831.50 |
| mixed | 50 | 11.46 | 348.68 | 11.77 | 832.05 |
| ramp | 1→100 | 407.78 | 270.08 | 5.50 | 966.61 |
| read_large | 1 | 18.65 | 196.22 | 18.65 | 830.37 |
| read_large | 10 | 12.06 | 350.88 | 12.06 | 845.64 |
| read_large | 50 | 11.65 | 347.92 | 11.65 | 848.78 |
| read_small | 1 | 16.62 | 203.52 | 16.62 | 804.43 |
| read_small | 10 | 11.76 | 345.26 | 11.76 | 803.76 |
| read_small | 50 | 11.34 | 346.94 | 11.34 | 810.56 |
| scheduler_large | 1 | 69.84 | 167.39 | — | 840.25 |
| scheduler_small | 1 | 34.92 | 84.96 | — | 805.73 |
| soak | 25 | 926.10 | 306.44 | 5.74 | 1,091.90 |

Trong soak, RSS bắt đầu khoảng 788–792 MiB, kết thúc 1.060–1.067 MiB; peak khoảng 1.091–1.096 MiB. Dữ liệu được tạo thêm liên tục, JVM/DB có cache và bộ nhớ ngoài heap; **chưa đủ chứng cứ kết luận memory leak**. RPS ở cuối soak cao hơn đầu, p95 giảm, không thấy suy giảm hiệu năng trong 5 phút đo. CPU backend + PostgreSQL khoảng 306–308% một core; không bao gồm CPU của client.

## 7. Nhận xét và hướng Pha 2

| Dấu hiệu đo được | Diễn giải và hướng kiểm chứng P2 |
|---|---|
| bid_hot: 89,71 → 29,93 bid thành công/s ở 1→50 worker; 409 tới 88% | Tranh chấp cùng phiên và luật không bid khi dẫn đầu/giá lỗi thời; nghiên cứu đoạn khóa và điều phối bid, giữ audit tính đúng |
| bid_many: 263,16 → 211,93 bid/s ở 10→50 worker | Trải phiên tốt hơn một hotspot nhưng vẫn giảm khi tải cao; đo lock/pool/CPU trước khi chọn cơ chế xử lý |
| login: khoảng 31 login/s ở cả 10 và 50 worker; p95 383 → 2.783 ms | Quan sát phù hợp với giới hạn xử lý xác thực; BCrypt là giả thuyết từ code, cần profiler để xác nhận. Thử backpressure/rate limit; không giảm bảo mật chỉ để tăng RPS |
| GET xác thực: 423,06 → 420,63 RPS ở 10→50 worker; p95 36 → 216 ms | Tăng đồng thời làm chờ nhiều hơn mà throughput gần như đứng; xem pool, đường xử lý JWT và truy cập user |
| Đọc 2.000 so với 100 phiên ở C50: 300,59 so với 308,44 RPS | Chênh lệch nhỏ trong dataset hiện tại; chưa có bằng chứng mạnh để khẳng định truy vấn đọc lớn là nghẽn chính. Xem từng operation/min–max trước khi chọn cache/index |
| Scheduler 500 phiên: p95 đóng khoảng 4,51 giây | Có cơ sở đánh giá batch/truy vấn phiên đến hạn/worker; giữ cùng thời gian và tiêu chí thắng để so sánh |

Các hướng trên là đề xuất dựa vào dấu hiệu, không phải kết luận nguyên nhân đã được chứng minh. Không so soak với mixed ngắn như một cải tiến: thời lượng, dữ liệu và mức warmup khác nhau. Ba lần lặp/min–max chưa phải kiểm định thống kê.

## 8. Cách dùng làm baseline Pha 2

Giữ nguyên ZIP kết quả, ZIP source P1 và notebook/config đã chạy. Khi có P2, đo lại cả P1/P2 cùng kernel, cùng harness/config/dữ liệu/ngân sách tài nguyên; chạy xen kẽ. Không ghép trực tiếp P2 ở session mới với P1 ở session này để công bố % cải thiện. Dùng `compare_benchmarks.py` và đọc latency/RPS thành công/lỗi/409/tính đúng cùng nhau.

## 9. Nguồn và tính toàn vẹn

- ZIP SHA256: `3213478c0eaed305dd84b1e309433d9fe9b4bb9249044ce6834cae7311922cad`
- Source SHA256 ghi trong report: `5a6978bfb71fbc99e8daccc06495db7110571c2e0b4b5a77f67058db067dec05`
- Harness SHA256: `da97669a9266c3f9c3313cf3c8d009a75c5bfc21d4da33eee5f4befd183b9f62`
- `summary.json`: median/min/max từng metric, từng operation, các stage ramp và metadata gốc.
- `validation.json`: đối chiếu ma trận/report/raw request. Audit nghiệp vụ là kết quả runner ghi nhận; phân tích này không chạy lại database Kaggle.
- ZIP gốc không bị sửa; không đưa raw request/token vào báo cáo này.
