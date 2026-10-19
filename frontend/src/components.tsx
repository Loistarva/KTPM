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
  ArrowRight,
  Clock3,
  ImageIcon,
  Plus,
  Radio,
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
      {children && <div className="heading-actions">{children}</div>}
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
  description,
  children,
}: {
  title?: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Gavel size={29} strokeWidth={1.5} />
      </span>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
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
      <span className="placeholder-icon">
        <ImageIcon size={36} strokeWidth={1.2} />
      </span>
      <span>Chưa có ảnh sản phẩm</span>
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
          <span className="card-arrow">
            <ArrowUpRight size={21} />
          </span>
        </div>
        <div className="card-end">
          <span>
            <Clock3 size={13} />
            {auction.status === "SCHEDULED"
              ? "Bắt đầu sau"
              : auction.status === "ACTIVE"
                ? "Thời gian còn lại"
                : "Đã kết thúc"}
          </span>
          {["ACTIVE", "SCHEDULED"].includes(auction.status) ? (
            <Countdown
              until={
                auction.status === "SCHEDULED"
                  ? auction.startingTime
                  : auction.endingTime
              }
              compact
            />
          ) : (
            <time dateTime={auction.endingTime}>
              {date(auction.endingTime)}
            </time>
          )}
        </div>
      </div>
    </Link>
  );
}
export function Countdown({
  until,
  compact = false,
}: {
  until: string;
  compact?: boolean;
}) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const seconds = Math.max(0, Math.floor((Date.parse(until) - now) / 1000));
  const parts = [
    Math.floor(seconds / 86400),
    Math.floor(seconds / 3600) % 24,
    Math.floor(seconds / 60) % 60,
    seconds % 60,
  ];
  if (compact)
    return (
      <strong className="countdown-compact">
        {parts[0] > 0 ? `${parts[0]}n ` : ""}
        {parts
          .slice(1)
          .map((n) => String(n).padStart(2, "0"))
          .join(":")}
      </strong>
    );
  return (
    <div className="countdown" aria-label="Thời gian còn lại">
      {parts.map((value, i) => (
        <div key={i}>
          <strong>{String(value).padStart(2, "0")}</strong>
          <span>{["Ngày", "Giờ", "Phút", "Giây"][i]}</span>
        </div>
      ))}
    </div>
  );
}

export function AuctionSkeleton() {
  return (
    <div className="auction-grid" role="status">
      <span className="sr-only">Đang tải phiên đấu giá…</span>
      {Array.from({ length: 3 }, (_, i) => (
        <div className="auction-card skeleton-card" key={i} aria-hidden="true">
          <div className="skeleton skeleton-image" />
          <div className="card-body">
            <div className="skeleton skeleton-line short" />
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line medium" />
          </div>
        </div>
      ))}
    </div>
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
      <a className="skip-link" href="#main-content">
        Bỏ qua đến nội dung
      </a>
      <div className="announcement">
        <div className="container announcement-inner">
          <span>Khám phá giá trị. Tìm điều bạn thích.</span>
          <span className="announcement-live">
            <Radio size={12} /> Minh bạch từng lượt đặt giá
          </span>
        </div>
      </div>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-icon">
              <Gavel size={23} />
            </span>
            <span className="brand-wordmark">
              KTPM<span className="brand-caption">AUCTION MARKETPLACE</span>
            </span>
          </Link>
          <nav
            aria-label="Điều hướng chính"
            className={"main-nav " + (menu ? "open" : "")}
          >
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) =>
                  isActive ||
                  (link.to === "/auctions" && location.pathname === "/")
                    ? "active"
                    : ""
                }
              >
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
            <Link className="header-create" to="/selling/auctions/new">
              <Plus size={16} /> Tạo phiên
            </Link>
            {user ? (
              <>
                <span className="user-link">
                  <span className="user-avatar">
                    {user.username.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="user-name">
                    {user.username}
                    <small>
                      {user.role === "ADMIN" ? "Quản trị viên" : "Thành viên"}
                    </small>
                  </span>
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
              <Link className="button small header-login" to="/login">
                <UserRound size={15} /> Đăng nhập
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
      <main className="container main-content" id="main-content">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <div>
            <Link to="/" className="brand">
              <span className="brand-icon">
                <Gavel size={22} />
              </span>
              <span className="brand-wordmark">
                KTPM<span className="brand-caption">AUCTION MARKETPLACE</span>
              </span>
            </Link>
            <p>
              Mỗi món đồ có một câu chuyện.
              <br />
              Mỗi lượt đặt giá mở ra một cơ hội.
            </p>
          </div>
          <div className="footer-links">
            <strong>Khám phá</strong>
            <Link to="/auctions">Tất cả phiên đấu giá</Link>
            <Link to="/auctions?status=ACTIVE">
              Đang diễn ra <ArrowUpRight size={13} />
            </Link>
            <Link to="/auctions?status=SCHEDULED">Sắp bắt đầu</Link>
          </div>
          <div className="footer-links">
            <strong>Dành cho bạn</strong>
            <Link to="/selling/auctions/new">Tạo phiên đấu giá</Link>
            <Link to="/selling">Phiên của tôi</Link>
            <Link to="/activity">Lịch sử đặt giá</Link>
          </div>
          <div className="footer-note">
            <span className="live-label">
              <span className="live-dot" /> Cập nhật trực tiếp
            </span>
            <p>
              Giá và kết quả tự động đồng bộ.
              <br />
              Thời gian Việt Nam · UTC+7
            </p>
          </div>
        </div>
        <div className="container footer-bottom">
          <span>
            © {new Date().getFullYear()} KTPM Auction. Dự án thực hành.
          </span>
          <span>
            Đặt giá theo cách của bạn <ArrowRight size={13} />
          </span>
        </div>
      </footer>
    </>
  );
}
