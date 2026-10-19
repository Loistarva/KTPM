import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  RefreshCw,
  Search,
  ArrowRight,
  ArrowUpRight,
  Gavel,
  Radio,
  ShieldCheck,
  Sparkles,
  Plus,
  X,
  MousePointer2,
} from "lucide-react";
import {
  AuctionCard,
  Heading,
  AuctionSkeleton,
  ErrorBox,
  Empty,
  Pager,
  labels,
} from "../components";
import { useApi } from "../lib/queries";
import type { Auction, Page } from "../types";
export function ExplorePage({ home = false }: { home?: boolean }) {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get("search") || "");
  const page = Math.max(0, Number(params.get("page")) || 0);
  const status = params.get("status") || "";
  const statuses = ["", "ACTIVE", "SCHEDULED", "ENDED", "FAILED"];
  const query = new URLSearchParams({ page: String(page), size: "12" });
  if (status) query.set("status", status);
  if (params.get("search")) query.set("search", params.get("search")!);
  const data = useApi<Page<Auction>>(`/api/auctions?${query}`);
  const change = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    value ? next.set(key, value) : next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  };
  return (
    <>
      {home && (
        <section className="hero">
          <div className="hero-copy">
            <span className="hero-kicker">
              <span className="live-dot" /> KHÁM PHÁ. ĐẶT GIÁ. SỞ HỮU.
            </span>
            <h1>
              Thứ bạn thích.
              <br />
              <span>Giá bạn chọn.</span>
            </h1>
            <p>
              Những món đồ thú vị đang chờ chủ nhân mới. Khám phá phiên đấu giá,
              chọn mức giá của bạn và bắt đầu một câu chuyện mới.
            </p>
            <div className="hero-actions">
              <a className="button" href="#auctions">
                Khám phá các phiên <ArrowUpRight size={18} />
              </a>
              <Link className="hero-secondary" to="/selling/auctions/new">
                Bắt đầu bán <ArrowRight size={16} />
              </Link>
            </div>
            <div className="hero-assurance">
              <ShieldCheck size={16} />
              <span>
                Công khai từng lượt giá. Cập nhật theo thời gian thực.
              </span>
            </div>
          </div>
          <div className="hero-art">
            <img
              src="/images/auction-editorial.webp"
              alt="Máy ảnh cổ điển, đồng hồ và tai nghe trong không gian trưng bày ấm áp"
              width={1536}
              height={1024}
              decoding="async"
            />
            <span className="hero-art-label">
              <Sparkles size={14} /> MÓN ĐỒ HAY, CƠ HỘI MỚI
            </span>
            <div className="hero-art-note">
              <span className="art-note-icon">
                <Gavel size={21} />
              </span>
              <div>
                <strong>Mỗi món đồ, một giá trị.</strong>
                <span>Bạn là người quyết định mức giá.</span>
              </div>
              <ArrowUpRight size={20} />
            </div>
          </div>
        </section>
      )}
      {home && (
        <div className="trust-strip">
          {[
            {
              icon: Radio,
              title: "Theo dõi trực tiếp",
              text: "Giá mới được cập nhật tự động",
            },
            {
              icon: MousePointer2,
              title: "Chủ động mức giá",
              text: "Tự chọn mỗi lượt đặt giá của bạn",
            },
            {
              icon: ShieldCheck,
              title: "Rõ ràng kết quả",
              text: "Lịch sử công khai, người thắng minh bạch",
            },
          ].map((item) => (
            <div className="trust-item" key={item.title}>
              <span>
                <item.icon size={21} strokeWidth={1.5} />
              </span>
              <div>
                <strong>{item.title}</strong>
                <p>{item.text}</p>
              </div>
            </div>
          ))}
        </div>
      )}
      <section className="catalog" id="auctions">
        <Heading
          eyebrow="TÌM MÓN ĐỒ TIẾP THEO CỦA BẠN"
          title={home ? "Khám phá các phiên" : "Các phiên đấu giá"}
          description="Một lựa chọn mới. Một cơ hội để sở hữu."
        >
          <span className="live-label">
            <span className="live-dot" /> Đấu giá trực tiếp
          </span>
          <button
            className="button secondary"
            onClick={() => void data.refetch()}
          >
            <RefreshCw
              size={14}
              className={data.isFetching ? "refreshing" : ""}
            />
            Làm mới
          </button>
        </Heading>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            change("search", search.trim());
          }}
        >
          <div className="search-input">
            <Search size={18} />
            <input
              aria-label="Tìm tên món hàng"
              placeholder="Bạn đang tìm món đồ nào?"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              maxLength={200}
            />
          </div>
          <button className="button" type="submit">
            Tìm kiếm <ArrowRight size={15} />
          </button>
          <select
            className="mobile-status-select"
            aria-label="Trạng thái"
            value={status}
            onChange={(e) => change("status", e.target.value)}
          >
            <option value="">Tất cả trạng thái</option>
            {["SCHEDULED", "ACTIVE", "ENDED", "FAILED"].map((s) => (
              <option key={s} value={s}>
                {labels[s]}
              </option>
            ))}
          </select>
        </form>
        <div className="catalog-toolbar">
          <div className="status-filters" aria-label="Lọc trạng thái phiên">
            {statuses.map((s) => (
              <button
                type="button"
                className={
                  status === s ? "filter-chip selected" : "filter-chip"
                }
                aria-pressed={status === s}
                key={s}
                onClick={() => change("status", s)}
              >
                {s === "ACTIVE" && <span className="live-dot" />}
                {s ? labels[s] : "Tất cả phiên"}
              </button>
            ))}
          </div>
          {data.data && (
            <span className="result-count">
              <strong>{data.data.totalElements}</strong> phiên đấu giá
            </span>
          )}
        </div>
        {params.get("search") && (
          <div className="search-summary">
            Kết quả cho “{params.get("search")}”
            <button
              type="button"
              onClick={() => {
                setSearch("");
                change("search", "");
              }}
              aria-label="Xóa tìm kiếm"
            >
              <X size={13} />
            </button>
          </div>
        )}
        {data.isLoading ? (
          <AuctionSkeleton />
        ) : data.error ? (
          <ErrorBox error={data.error} retry={() => void data.refetch()} />
        ) : !data.data?.content.length ? (
          <Empty
            title={
              status || params.get("search")
                ? "Chưa tìm thấy món đồ phù hợp"
                : "Mỗi phiên hay đều có một khởi đầu"
            }
            description={
              status || params.get("search")
                ? "Thử một từ khóa khác hoặc mở rộng bộ lọc để khám phá thêm."
                : "Hiện chưa có phiên đấu giá. Hãy đưa món đồ đầu tiên của bạn lên sàn và tìm chủ nhân mới."
            }
          >
            {status || params.get("search") ? (
              <button
                className="button secondary"
                onClick={() => {
                  setSearch("");
                  setParams({});
                }}
              >
                Xóa bộ lọc <X size={15} />
              </button>
            ) : (
              <Link className="button" to="/selling/auctions/new">
                <Plus size={16} /> Tạo phiên đầu tiên
              </Link>
            )}
          </Empty>
        ) : (
          <div className="auction-grid">
            {data.data.content.map((a) => (
              <AuctionCard key={String(a.id)} auction={a} />
            ))}
          </div>
        )}
        <Pager data={data.data} onChange={(p) => change("page", String(p))} />
      </section>
      {home && (
        <>
          <section className="how-it-works">
            <div className="section-intro">
              <div>
                <p className="eyebrow">ĐƠN GIẢN TỪ LƯỢT ĐẦU TIÊN</p>
                <h2>Ba bước, một món đồ yêu thích.</h2>
              </div>
              <span>Khám phá hành trình đấu giá của bạn.</span>
            </div>
            <div className="steps-grid">
              {[
                {
                  icon: Search,
                  title: "Tìm điều bạn thích",
                  text: "Dạo qua các phiên, xem chi tiết sản phẩm và lịch sử đặt giá công khai.",
                },
                {
                  icon: Gavel,
                  title: "Chọn mức giá của bạn",
                  text: "Đăng nhập, nhập mức giá hợp lệ và theo dõi diễn biến ngay khi có thay đổi.",
                },
                {
                  icon: Sparkles,
                  title: "Chờ khoảnh khắc chiến thắng",
                  text: "Khi phiên khép lại, giá cuối cùng và người thắng sẽ tự động được công bố.",
                },
              ].map((item, i) => (
                <article className="step-card" key={item.title}>
                  <div className="step-top">
                    <span className="step-number">0{i + 1}</span>
                    <item.icon size={23} strokeWidth={1.5} />
                  </div>
                  <h3>{item.title}</h3>
                  <p>{item.text}</p>
                </article>
              ))}
            </div>
          </section>
          <section className="seller-banner">
            <div>
              <p className="eyebrow">ĐẾN LƯỢT MÓN ĐỒ CỦA BẠN</p>
              <h2>
                Trao món đồ cũ.
                <br />
                Mở một câu chuyện mới.
              </h2>
            </div>
            <Link className="button lime" to="/selling/auctions/new">
              Tạo phiên đấu giá <ArrowUpRight size={18} />
            </Link>
          </section>
        </>
      )}
    </>
  );
}
