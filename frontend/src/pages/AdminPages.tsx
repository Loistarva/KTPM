import { Link, NavLink, Outlet, useSearchParams } from "react-router-dom";
import { useAuth } from "../context";
import { api } from "../lib/api";
import { useApi, useAction } from "../lib/queries";
import {
  Heading,
  Loading,
  ErrorBox,
  Pager,
  Confirm,
  Empty,
  Status,
} from "../components";
import { sameId, money } from "../lib/money";
import type { Profile, Auction, Page } from "../types";
export function AdminLayout() {
  return (
    <>
      <Heading
        eyebrow="ADMIN"
        title="Dọn dữ liệu thử"
        description="Xóa hẳn dữ liệu; thao tác không thể hoàn tác."
      />
      <nav className="tabs">
        <NavLink to="/admin/users">Tài khoản</NavLink>
        <NavLink to="/admin/auctions">Phiên đấu giá</NavLink>
      </nav>
      <Outlet />
    </>
  );
}
export function AdminHome() {
  return (
    <div className="panel">
      <p>Chọn tài khoản hoặc phiên để dọn dữ liệu thử.</p>
      <Link className="button" to="/admin/auctions">
        Xem phiên
      </Link>
    </div>
  );
}
export function AdminUsers() {
  const auth = useAuth();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const data = useApi<Page<Profile>>(`/api/admin/users?page=${page}&size=20`);
  const remove = useAction<string>(
    (id) => api(`/api/admin/users/${id}`, { method: "DELETE" }),
    "Đã xóa tài khoản và dữ liệu liên quan.",
  );
  return (
    <>
      <Heading title="Tài khoản">
        <button
          className="button secondary"
          onClick={() => void data.refetch()}
        >
          Làm mới
        </button>
      </Heading>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorBox error={data.error} />
      ) : (
        <div className="table-wrap panel">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Tài khoản</th>
                <th>Email</th>
                <th>Quyền</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {data.data?.content.map((u) => (
                <tr key={String(u.id)}>
                  <td>{u.id}</td>
                  <td>{u.username}</td>
                  <td>{u.email}</td>
                  <td>{u.role}</td>
                  <td>
                    {sameId(auth.user?.id, u.id) ? (
                      "Đang đăng nhập"
                    ) : (
                      <Confirm
                        title={`Xóa tài khoản ${u.username}?`}
                        trigger="Xóa tài khoản"
                        danger
                        pending={remove.isPending}
                        onConfirm={() => remove.mutateAsync(String(u.id))}
                      >
                        Xóa hẳn tài khoản, các phiên họ tạo và toàn bộ bid của
                        họ. Giá/người thắng ở phiên khác sẽ được tính lại, kể cả
                        phiên đã kết thúc.
                      </Confirm>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager
        data={data.data}
        onChange={(p) => setParams({ page: String(p) })}
      />
    </>
  );
}
export function AdminAuctions() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const data = useApi<Page<Auction>>(
    `/api/admin/auctions?page=${page}&size=20`,
  );
  const remove = useAction<string>(
    (id) => api(`/api/admin/auctions/${id}`, { method: "DELETE" }),
    "Đã xóa phiên và lịch sử bid.",
  );
  return (
    <>
      <Heading title="Phiên đấu giá">
        <button
          className="button secondary"
          onClick={() => void data.refetch()}
        >
          Làm mới
        </button>
      </Heading>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorBox error={data.error} />
      ) : !data.data?.content.length ? (
        <Empty />
      ) : (
        <div className="table-wrap panel">
          <table>
            <thead>
              <tr>
                <th>Phiên</th>
                <th>Người bán</th>
                <th>Giá</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {data.data.content.map((a) => (
                <tr key={String(a.id)}>
                  <td>
                    <Link to={`/auctions/${a.id}`}>
                      #{a.id} · {a.name}
                    </Link>
                  </td>
                  <td>#{a.sellerId}</td>
                  <td>{money(a.currentPrice)}</td>
                  <td>
                    <Status value={a.status} />
                  </td>
                  <td>
                    <Confirm
                      title={`Xóa phiên #${a.id}?`}
                      trigger="Xóa phiên"
                      danger
                      pending={remove.isPending}
                      onConfirm={() => remove.mutateAsync(String(a.id))}
                    >
                      Xóa hẳn phiên, thông tin món hàng và tất cả lượt đặt giá
                      của phiên, kể cả khi đã kết thúc.
                    </Confirm>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager
        data={data.data}
        onChange={(p) => setParams({ page: String(p) })}
      />
    </>
  );
}
