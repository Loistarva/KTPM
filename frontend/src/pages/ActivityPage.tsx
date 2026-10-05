import { useState } from "react";
import { Link } from "react-router-dom";
import { RefreshCw } from "lucide-react";
import { useApi } from "../lib/queries";
import { date, money } from "../lib/money";
import {
  Empty,
  ErrorBox,
  Heading,
  Loading,
  Pager,
  Status,
} from "../components";
import type { Bid, Page } from "../types";
export function ActivityPage() {
  const [page, setPage] = useState(0);
  const bids = useApi<Page<Bid>>(`/api/users/me/bids?page=${page}&size=10`);
  return (
    <>
      <Heading
        title="Hoạt động của tôi"
        eyebrow="LỊCH SỬ"
        description="Các lượt đặt giá của bạn."
      >
        <button
          className="button secondary"
          onClick={() => void bids.refetch()}
        >
          <RefreshCw size={16} />
          Làm mới
        </button>
      </Heading>
      <section className="panel">
        {bids.isLoading ? (
          <Loading />
        ) : bids.error ? (
          <ErrorBox error={bids.error} retry={() => void bids.refetch()} />
        ) : !bids.data?.content.length ? (
          <Empty />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Phiên</th>
                  <th>Mức giá</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                </tr>
              </thead>
              <tbody>
                {bids.data.content.map((b) => (
                  <tr key={b.id}>
                    <td>
                      <Link to={`/auctions/${b.auctionId}`}>
                        Phiên #{b.auctionId} ↗
                      </Link>
                    </td>
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
        <Pager data={bids.data} onChange={setPage} />
      </section>
    </>
  );
}
