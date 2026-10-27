import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  RefreshCw,
  ArrowLeft,
  ArrowUpRight,
  ChevronRight,
  Clock3,
  ShieldCheck,
  UserRound,
  Trophy,
  Gavel,
} from "lucide-react";
import { useAuth } from "../context";
import { api } from "../lib/api";
import { useApi, useAction } from "../lib/queries";
import { useRealtimeStatus } from "../lib/realtime";
import {
  minimumBid,
  money,
  date,
  sameId,
  validateMoney,
  dec,
} from "../lib/money";
import {
  AuctionImage,
  Status,
  Field,
  Loading,
  ErrorBox,
  Heading,
  Pager,
  Empty,
  Countdown,
  labels,
} from "../components";
import type { Auction, Bid, Page } from "../types";
export function AuctionPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const auth = useAuth();
  const realtime = useRealtimeStatus();
  const data = useApi<Auction>(`/api/auctions/${id}`);
  const history = useApi<Page<Bid>>(
    `/api/auctions/${id}/bids?page=${page}&size=20`,
  );
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const bidInput = useRef<HTMLInputElement>(null);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    setAmount("");
    setError("");
  }, [id]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const mutation = useAction<string>(
    (value) =>
      api(`/api/auctions/${id}/bids`, {
        method: "POST",
        body: { amount: value },
      }),
    "Đã đặt giá.",
    () => {
      setAmount("");
      setError("");
    },
  );
  if (data.isLoading) return <Loading />;
  if (data.error)
    return <ErrorBox error={data.error} retry={() => void data.refetch()} />;
  if (!data.data) return <Empty />;
  const a = data.data;
  const expired = now >= Date.parse(a.endingTime);
  const active = a.status === "ACTIVE" && !expired;
  const owner = sameId(auth.user?.id, a.sellerId);
  const leader = sameId(auth.user?.id, a.winnerUserId);
  const normalizedAmount = amount.trim();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (mutation.isPending) return;
    const problem = validateMoney(normalizedAmount);
    if (problem) {
      setError(problem);
      bidInput.current?.focus();
      return;
    }
    if (dec(normalizedAmount).lt(minimumBid(a))) {
      setError("Giá tối thiểu là " + money(minimumBid(a).toString()));
      bidInput.current?.focus();
      return;
    }
    setError("");
    try {
      await mutation.mutateAsync(normalizedAmount);
    } catch {}
  }
  return (
    <>
      <nav className="breadcrumbs" aria-label="Đường dẫn">
        <Link to="/auctions">
          <ArrowLeft size={14} /> Khám phá
        </Link>
        <ChevronRight size={13} />
        <span>Phiên #{a.id}</span>
      </nav>
      <Heading eyebrow={`PHIÊN #${a.id}`} title={a.name}>
        <button
          className="button secondary"
          onClick={() => {
            void data.refetch();
            void history.refetch();
          }}
        >
          <RefreshCw size={16} />
          Làm mới
        </button>
      </Heading>
      <p
        className={
          "realtime-indicator " + (realtime ? "connected" : "reconnecting")
        }
        role="status"
      >
        <span className="live-dot" />
        {realtime
          ? "Đang cập nhật trực tiếp"
          : "Đang kết nối lại · tự động làm mới mỗi 10 giây"}
      </p>
      <nav className="detail-section-links" aria-label="Nội dung phiên đấu giá">
        <a href="#bid-panel">
          <Gavel size={15} /> {active ? "Đặt giá" : "Thông tin phiên"}
        </a>
        <a href="#product-info">Thông tin món hàng</a>
        <a href="#bid-history">
          Lịch sử đặt giá{" "}
          {history.data && <span>{history.data.totalElements}</span>}
        </a>
      </nav>
      <div className="detail-grid">
        <section className="product-column">
          <div className="product-gallery">
            <AuctionImage auction={a} className="detail-image" />
            <span className="gallery-label">
              {labels[a.condition] || a.condition}
            </span>
          </div>
          <div className="panel product-description" id="product-info">
            <p className="eyebrow">TÌM HIỂU TRƯỚC KHI ĐẶT GIÁ</p>
            <h2>Thông tin món hàng</h2>
            <p className="description">{a.description}</p>
            <div className="product-metadata">
              <span>
                <ShieldCheck size={15} /> {labels[a.condition] || a.condition}
              </span>
              <span>
                <UserRound size={15} /> Người bán: #{a.sellerId}
              </span>
            </div>
          </div>
        </section>
        <section
          className="panel bid-panel"
          id="bid-panel"
          aria-label="Thông tin và đặt giá"
        >
          <div className="bid-panel-heading">
            <span className="eyebrow">
              <Gavel size={15} />{" "}
              {active
                ? "ĐẤU GIÁ TRỰC TIẾP"
                : a.status === "SCHEDULED"
                  ? "SẮP DIỄN RA"
                  : "KẾT QUẢ PHIÊN"}
            </span>
            <Status value={a.status} />
          </div>
          <div className="bid-price-block">
            <p className="muted">Giá hiện tại</p>
            <h2 className="large-price" data-testid="auction-price">
              {money(a.currentPrice)}
            </h2>
            {a.winnerUserId != null && a.status === "ACTIVE" && (
              <span className="leader-label">
                <UserRound size={13} /> Đang dẫn đầu: #{a.winnerUserId}
              </span>
            )}
          </div>
          {["ACTIVE", "SCHEDULED"].includes(a.status) && !expired && (
            <div className="countdown-panel">
              <p>
                <Clock3 size={14} />{" "}
                {a.status === "SCHEDULED" ? "Bắt đầu sau" : "Thời gian còn lại"}
              </p>
              <Countdown
                until={a.status === "SCHEDULED" ? a.startingTime : a.endingTime}
              />
            </div>
          )}
          <dl className="facts">
            <dt>Giá khởi điểm</dt>
            <dd>{money(a.startingPrice)}</dd>
            <dt>Bước giá</dt>
            <dd>{money(a.minimumBidStep)}</dd>
            <dt>Bắt đầu</dt>
            <dd>{date(a.startingTime)}</dd>
            <dt>Kết thúc</dt>
            <dd>{date(a.endingTime)}</dd>
          </dl>
          {a.status === "ENDED" ? (
            <div className="result-box">
              <Trophy size={24} />
              <h3>Người thắng: #{a.winnerUserId}</h3>
              <p>Giá cuối: {money(a.finalPrice)}</p>
            </div>
          ) : a.status === "FAILED" ? (
            <p className="auction-note">Phiên kết thúc không có người thắng.</p>
          ) : (
            <p className="auction-note">
              {expired
                ? "Đã hết giờ. Đang chờ kết quả tự động cập nhật."
                : a.status === "SCHEDULED"
                  ? "Phiên chưa bắt đầu."
                  : "Giá và kết quả được cập nhật theo thời gian thực."}
            </p>
          )}
          {!auth.user && !active ? (
            <Link
              className="button secondary full"
              to="/auctions?status=ACTIVE"
            >
              Khám phá phiên đang diễn ra <ArrowUpRight size={17} />
            </Link>
          ) : !auth.user ? (
            <Link
              className="button full"
              to={`/login?returnTo=/auctions/${a.id}`}
            >
              Đăng nhập để đặt giá <ArrowUpRight size={17} />
            </Link>
          ) : owner ? (
            <p className="owner-note">
              <ShieldCheck size={17} /> Bạn không thể đặt giá phiên của mình.
            </p>
          ) : active ? (
            <form className="bid-form" onSubmit={submit}>
              {leader && (
                <p className="leader-note">
                  <Trophy size={16} /> Bạn đang dẫn đầu phiên này.
                </p>
              )}
              <Field
                label="Giá đặt"
                hint={`Tối thiểu ${money(minimumBid(a).toString())}`}
                error={error}
              >
                <input
                  ref={bidInput}
                  value={amount}
                  className="bid-amount-input"
                  placeholder="Nhập mức giá của bạn"
                  disabled={leader}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    setError("");
                  }}
                  inputMode="decimal"
                  required
                  maxLength={20}
                />
              </Field>
              <div className="quick-bids" aria-label="Gợi ý mức giá">
                {[0, 1, 5].map((steps) => (
                  <button
                    type="button"
                    key={steps}
                    disabled={leader || mutation.isPending}
                    onClick={() => {
                      setAmount(
                        minimumBid(a)
                          .plus(dec(a.minimumBidStep).times(steps))
                          .toString(),
                      );
                      setError("");
                    }}
                  >
                    {steps === 0 ? "Giá tối thiểu" : `+${steps} bước giá`}
                  </button>
                ))}
              </div>
              {normalizedAmount &&
                !validateMoney(normalizedAmount) &&
                !leader && (
                  <div
                    className={
                      "bid-review " +
                      (dec(normalizedAmount).lt(minimumBid(a))
                        ? "below-minimum"
                        : "")
                    }
                    role="status"
                    aria-label="Mức giá chuẩn bị gửi"
                  >
                    <span>Mức giá chuẩn bị gửi</span>
                    <strong>{money(normalizedAmount)}</strong>
                    <small>
                      {dec(normalizedAmount).lt(minimumBid(a))
                        ? `Chưa đạt mức tối thiểu ${money(minimumBid(a).toString())}.`
                        : "Chỉ gửi khi bạn bấm Đặt giá."}
                    </small>
                  </div>
                )}
              <button
                className="button full bid-submit"
                disabled={mutation.isPending || leader}
                onClick={(e) => {
                  if (e.detail > 1) e.preventDefault();
                }}
              >
                {mutation.isPending ? "Đang gửi…" : "Đặt giá"}
                <ArrowUpRight size={18} />
              </button>
            </form>
          ) : null}
          <p className="bid-assurance">
            <ShieldCheck size={13} /> Lịch sử công khai · Kết quả minh bạch
          </p>
        </section>
      </div>
      <section className="panel bid-history" id="bid-history">
        <div className="history-heading">
          <div>
            <p className="eyebrow">DIỄN BIẾN CỦA PHIÊN</p>
            <h2>Lịch sử đặt giá</h2>
          </div>
          {history.data && (
            <span className="count-badge">
              {history.data.totalElements} lượt đặt giá
            </span>
          )}
        </div>
        {history.isLoading ? (
          <Loading />
        ) : history.error ? (
          <ErrorBox error={history.error} />
        ) : !history.data?.content.length ? (
          <Empty title="Chưa có lượt đặt giá" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Tài khoản</th>
                  <th>Giá</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {history.data.content.map((b) => (
                  <tr
                    key={String(b.id)}
                    className={
                      ["WINNING", "WON"].includes(b.status) ? "winning-row" : ""
                    }
                  >
                    <td>#{b.bidderId}</td>
                    <td className="bid-history-amount">{money(b.amount)}</td>
                    <td>
                      <Status value={b.status} />
                    </td>
                    <td>{date(b.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager
          data={history.data}
          onChange={(p) => setParams({ page: String(p) })}
        />
      </section>
      {active && !owner && (
        <div className="mobile-bid-bar" aria-label="Lối tắt đặt giá">
          <div>
            <span>{leader ? "Bạn đang dẫn đầu" : "Giá hiện tại"}</span>
            <strong>{money(a.currentPrice)}</strong>
          </div>
          {!auth.user ? (
            <Link className="button" to={`/login?returnTo=/auctions/${a.id}`}>
              Đăng nhập <ArrowUpRight size={17} />
            </Link>
          ) : (
            <a className="button" href={leader ? "#bid-history" : "#bid-panel"}>
              {leader ? "Xem lịch sử" : "Đến phần đặt giá"}{" "}
              <ArrowUpRight size={17} />
            </a>
          )}
        </div>
      )}
    </>
  );
}
