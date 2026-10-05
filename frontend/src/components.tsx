import {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from "react";
import { Link, NavLink, Outlet, useLocation, Navigate } from "react-router-dom";
import {
  ArrowUpRight,
  Gavel,
  Menu,
  Store,
  Activity,
  UserRound,
  ShieldCheck,
  LogOut,
  Search,
  RefreshCw,
  X,
} from "lucide-react";
import { useAuth, useNotice } from "./context";
import type { Auction, Page } from "./types";
import { date, money } from "./lib/money";
export const labels: Record<string, string> = {
  ACTIVE: "Đang diễn ra",
  SCHEDULED: "Sắp diễn ra",
  ENDED: "Đã kết thúc",
  FAILED: "Không thành công",
  WINNING: "Đang dẫn đầu",
  OUTBID: "Đã bị vượt",
  WON: "Thắng",
  LOST: "Không thắng",
  NEW: "Mới",
  USED: "Đã sử dụng",
};
export function Status({
  value,
  account = false,
}: {
  value: string;
  account?: boolean;
}) {
  return (
    <span className={"status status-" + value.toLowerCase()}>
      {account && value === "ACTIVE" ? "Hoạt động" : labels[value] || value}
    </span>
  );
}
export function Heading({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="muted">{description}</p>}
      </div>
      {children}
    </div>
  );
}
export function Loading() {
  return (
    <div className="empty" role="status">
      <span className="spinner" />
      Đang tải dữ liệu…
    </div>
  );
}
export function Empty({
  title = "Chưa có dữ liệu",
  children,
}: {
  title?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <Gavel size={30} />
      <h3>{title}</h3>
      {children}
    </div>
  );
}
export function ErrorBox({
  error,
  retry,
}: {
  error: Error | string;
  retry?: () => void;
}) {
  return (
    <div className="error-box" role="alert">
      <strong>Chưa thể hoàn tất</strong>
      <p>{typeof error === "string" ? error : error.message}</p>
      {retry && (
        <button className="button secondary" onClick={retry}>
          <RefreshCw size={16} />
          Thử lại
        </button>
      )}
    </div>
  );
}
export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {Children.map(children, (child) =>
        isValidElement(child) &&
        typeof child.type === "string" &&
        ["input", "select", "textarea"].includes(child.type)
          ? cloneElement(
              child as ReactElement<{
                "aria-label"?: string;
                "aria-invalid"?: boolean;
              }>,
              { "aria-label": label, "aria-invalid": !!error },
            )
          : child,
      )}
      {hint && <small>{hint}</small>}
      {error && <small className="error-text">{error}</small>}
    </label>
  );
}
export function Pager({
  data,
  onChange,
}: {
  data?: Page<unknown>;
  onChange: (page: number) => void;
}) {
  if (!data || data.totalPages < 2) return null;
  return (
    <nav aria-label="Phân trang" className="pager">
      <span>
        {data.totalElements} kết quả · Trang {data.page + 1}/{data.totalPages}
      </span>
      <div>
        <button
          className="button secondary"
          disabled={data.page === 0}
          onClick={() => onChange(data.page - 1)}
        >
          Trước
        </button>
        <button
          className="button secondary"
          disabled={data.page >= data.totalPages - 1}
          onClick={() => onChange(data.page + 1)}
        >
          Tiếp
        </button>
      </div>
    </nav>
  );
}
export function Confirm({
  title,
  children,
  onConfirm,
  pending,
  trigger,
  danger = false,
}: {
  title: string;
  children: ReactNode;
  onConfirm: () => Promise<unknown>;
  pending?: boolean;
  trigger: string;
  danger?: boolean;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  return (
    <>
      <button
        className={"button " + (danger ? "danger-outline" : "secondary")}
        disabled={pending}
        onClick={() => dialog.current?.showModal()}
      >
        {trigger}
      </button>
      <dialog
        ref={dialog}
        aria-label={title}
        onCancel={(e) => {
          if (pending) e.preventDefault();
        }}
      >
        <div className="dialog-content">
          <h2>{title}</h2>
          <div className="muted">{children}</div>
          <div className="actions">
            <button
              className="button secondary"
              disabled={pending}
              onClick={() => dialog.current?.close()}
            >
              Quay lại
            </button>
            <button
              className={"button " + (danger ? "danger" : "")}
              disabled={pending}
              onClick={async () => {
                try {
                  await onConfirm();
                  dialog.current?.close();
                } catch {
                  /* mutation owns visible error */
                }
              }}
            >
              {pending ? "Đang xử lý…" : "Xác nhận"}
            </button>
          </div>
        </div>
      </dialog>
    </>
  );
}
export function AuctionImage({
  auction,
  className = "",
}: {
  auction: Auction;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  const src = auction.imageUrl;
  useEffect(() => setFailed(false), [src]);
  return src && /^https?:\/\//i.test(src) && !failed ? (
    <img
      className={"auction-image " + className}
      src={src}
      alt={auction.name}
      loading="lazy"
      onError={() => setFailed(true)}
    />
  ) : (
    <div className={"image-placeholder " + className}>
      <Gavel size={44} />
      <span>KTPM AUCTION</span>
    </div>
  );
}
export function AuctionCard({ auction }: { auction: Auction }) {
  return (
    <Link className="auction-card" to={`/auctions/${auction.id}`}>
      <div className="card-image">
        <AuctionImage auction={auction} />
        <Status value={auction.status} />
      </div>
      <div className="card-body">
        <p className="card-meta">
          PHIÊN #{auction.id} · {labels[auction.condition] || auction.condition}
        </p>
        <h3>{auction.name}</h3>
        <div className="card-price">
          <div>
            <small>Giá hiện tại</small>
            <strong>{money(auction.currentPrice)}</strong>
          </div>
          <ArrowUpRight size={22} />
        </div>
        <p className="card-end">
          {auction.status === "SCHEDULED" ? "Bắt đầu" : "Kết thúc"}{" "}
          {date(
            auction.status === "SCHEDULED"
              ? auction.startingTime
              : auction.endingTime,
          )}
        </p>
      </div>
    </Link>
  );
}
export function Guard({ admin = false }: { admin?: boolean }) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading) return <Loading />;
  if (auth.error)
    return <ErrorBox error={auth.error} retry={() => void auth.reload()} />;
  if (!auth.user)
    return (
      <Navigate
        to={
          "/login?returnTo=" +
          encodeURIComponent(location.pathname + location.search)
        }
        replace
      />
    );
  if (admin && auth.user.role !== "ADMIN")
    return (
      <Empty title="Bạn không có quyền quản trị">
        <Link className="button" to="/">
          Về trang chủ
        </Link>
      </Empty>
    );
  return <Outlet />;
}
export function Layout() {
  const { user, logout } = useAuth();
  const notice = useNotice();
  const location = useLocation();
  const [menu, setMenu] = useState(false);
  useEffect(() => setMenu(false), [location.pathname]);
  const links = [
    { to: "/auctions", label: "Khám phá", icon: Search },
    ...(user
      ? [
          { to: "/selling", label: "Phiên của tôi", icon: Store },
          { to: "/activity", label: "Hoạt động", icon: Activity },
        ]
      : []),
  ];
  return (
    <>
      <div className="announcement">
        Sàn đấu giá KTPM <span>·</span> Minh bạch từng lượt đặt giá
      </div>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-icon">
              <Gavel size={23} />
            </span>
            KTPM<span className="brand-caption">AUCTION</span>
          </Link>
          <nav
            aria-label="Điều hướng chính"
            className={"main-nav " + (menu ? "open" : "")}
          >
            {links.map((link) => (
              <NavLink key={link.to} to={link.to}>
                <link.icon size={16} />
                {link.label}
              </NavLink>
            ))}
            {user?.role === "ADMIN" && (
              <NavLink to="/admin">
                <ShieldCheck size={16} />
                Quản trị
              </NavLink>
            )}
          </nav>
          <div className="header-user">
            {user ? (
              <>
                <span className="user-link">
                  <UserRound size={19} />
                  <span>{user.username}</span>
                </span>
                <button
                  className="icon-button"
                  aria-label="Đăng xuất"
                  onClick={async () => {
                    try {
                      await logout();
                      notice("Đã đăng xuất.");
                    } catch (e) {
                      notice((e as Error).message, true);
                    }
                  }}
                >
                  <LogOut size={18} />
                </button>
              </>
            ) : (
              <Link className="button small" to="/login">
                Đăng nhập
              </Link>
            )}
            <button
              className="icon-button mobile-menu"
              aria-label={menu ? "Đóng menu" : "Mở menu"}
              aria-expanded={menu}
              onClick={() => setMenu((v) => !v)}
            >
              {menu ? <X /> : <Menu />}
            </button>
          </div>
        </div>
      </header>
      <main className="container main-content">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <div>
            <Link to="/" className="brand">
              KTPM<span className="brand-caption">AUCTION</span>
            </Link>
            <p>Khám phá giá trị. Đặt giá theo cách của bạn.</p>
          </div>
          <div>
            <strong>Môi trường thử nghiệm</strong>
            <p>Đặt giá trực tiếp · Làm mới để theo dõi kết quả</p>
            <p>Thời gian hiển thị theo giờ Việt Nam (UTC+7)</p>
          </div>
        </div>
      </footer>
    </>
  );
}
