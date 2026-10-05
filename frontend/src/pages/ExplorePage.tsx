import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { RefreshCw, Search } from "lucide-react";
import {
  AuctionCard,
  Heading,
  Loading,
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
          <p className="eyebrow">KTPM AUCTION</p>
          <h1>
            Đấu giá đơn giản.
            <br />
            Bạn tự chọn mức giá.
          </h1>
          <p>
            Tạo phiên với thông tin món hàng và một ảnh. Đặt giá tay, làm mới để
            theo dõi kết quả.
          </p>
          <Link className="button" to="/selling/auctions/new">
            Tạo phiên đấu giá
          </Link>
        </section>
      )}
      <Heading
        title="Các phiên đấu giá"
        description="Làm mới để xem giá và trạng thái mới nhất."
      >
        <button
          className="button secondary"
          onClick={() => void data.refetch()}
        >
          <RefreshCw size={16} />
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
            placeholder="Tìm tên món hàng…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            maxLength={200}
          />
        </div>
        <button className="button" type="submit">
          Tìm kiếm
        </button>
        <select
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
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorBox error={data.error} retry={() => void data.refetch()} />
      ) : !data.data?.content.length ? (
        <Empty title="Chưa có phiên phù hợp" />
      ) : (
        <div className="auction-grid">
          {data.data.content.map((a) => (
            <AuctionCard key={String(a.id)} auction={a} />
          ))}
        </div>
      )}
      <Pager data={data.data} onChange={(p) => change("page", String(p))} />
    </>
  );
}
