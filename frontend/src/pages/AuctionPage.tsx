import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { RefreshCw } from "lucide-react";
import { useAuth } from "../context";
import { api } from "../lib/api";
import { useApi, useAction } from "../lib/queries";
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
} from "../components";
import type { Auction, Bid, Page } from "../types";
export function AuctionPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const auth = useAuth();
  const data = useApi<Auction>(`/api/auctions/${id}`);
  const history = useApi<Page<Bid>>(
    `/api/auctions/${id}/bids?page=${page}&size=20`,
  );
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
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
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (mutation.isPending) return;
    const problem = validateMoney(amount);
    if (problem) {
      setError(problem);
      return;
    }
    if (dec(amount).lt(minimumBid(a))) {
      setError("Giá tối thiểu là " + money(minimumBid(a).toString()));
      return;
    }
    setError("");
    try {
      await mutation.mutateAsync(amount.trim());
    } catch {}
  }
  return (
    <>
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
      <div className="detail-grid">
        <section className="panel">
          <AuctionImage auction={a} className="detail-image" />
          <h2>Thông tin món hàng</h2>
          <p className="description">{a.description}</p>
          <p>Tình trạng: {a.condition}</p>
          <p>Người bán: #{a.sellerId}</p>
        </section>
        <section className="panel">
          <Status value={a.status} />
          <p className="muted">Giá hiện tại</p>
          <h2 className="large-price" data-testid="auction-price">
            {money(a.currentPrice)}
          </h2>
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
              <h3>Người thắng: #{a.winnerUserId}</h3>
              <p>Giá cuối: {money(a.finalPrice)}</p>
            </div>
          ) : a.status === "FAILED" ? (
            <p>Phiên kết thúc không có người thắng.</p>
          ) : (
            <p>
              {expired
                ? "Đã hết giờ. Làm mới để xem kết quả."
                : a.status === "SCHEDULED"
                  ? "Phiên chưa bắt đầu."
                  : `Còn ${Math.max(0, Math.ceil((Date.parse(a.endingTime) - now) / 1000))} giây`}
            </p>
          )}
          {a.winnerUserId != null && a.status === "ACTIVE" && (
            <p>Đang dẫn đầu: #{a.winnerUserId}</p>
          )}
          {!auth.user ? (
            <Link className="button" to={`/login?returnTo=/auctions/${a.id}`}>
              Đăng nhập để đặt giá
            </Link>
          ) : owner ? (
            <p>Bạn không thể đặt giá phiên của mình.</p>
          ) : active ? (
            <form onSubmit={submit}>
              <Field
                label="Giá đặt"
                hint={`Tối thiểu ${money(minimumBid(a).toString())}`}
                error={error}
              >
                <input
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  inputMode="decimal"
                  required
                  maxLength={20}
                />
              </Field>
              <button
                className="button"
                disabled={mutation.isPending}
                onClick={(e) => {
                  if (e.detail > 1) e.preventDefault();
                }}
              >
                {mutation.isPending ? "Đang gửi…" : "Đặt giá"}
              </button>
            </form>
          ) : null}
        </section>
      </div>
      <section className="panel">
        <h2>Lịch sử đặt giá</h2>
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
                  <tr key={String(b.id)}>
                    <td>#{b.bidderId}</td>
                    <td>{money(b.amount)}</td>
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
    </>
  );
}
