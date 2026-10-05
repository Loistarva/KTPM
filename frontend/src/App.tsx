import { Component, type ReactNode } from "react";
import { Link, Route, Routes } from "react-router-dom";
import { Empty, ErrorBox, Guard, Layout } from "./components";
import { AuthPage } from "./pages/AuthPages";
import { ExplorePage } from "./pages/ExplorePage";
import { AuctionPage } from "./pages/AuctionPage";
import { ActivityPage } from "./pages/ActivityPage";
import { SellingPage, AuctionFormPage } from "./pages/SellingPages";
import {
  AdminLayout,
  AdminHome,
  AdminUsers,
  AdminAuctions,
} from "./pages/AdminPages";
export class ErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <div className="container main-content">
        <ErrorBox error="Giao diện gặp lỗi không mong đợi. Tải lại trang để thử lại." />
        <button className="button" onClick={() => location.reload()}>
          Tải lại trang
        </button>
      </div>
    ) : (
      this.props.children
    );
  }
}
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ExplorePage home />} />
        <Route path="auctions" element={<ExplorePage />} />
        <Route path="auctions/:id" element={<AuctionPage />} />
        <Route path="login" element={<AuthPage />} />
        <Route path="register" element={<AuthPage register />} />
        <Route element={<Guard />}>
          <Route path="activity" element={<ActivityPage />} />
          <Route path="selling" element={<SellingPage />} />
          <Route path="selling/auctions/new" element={<AuctionFormPage />} />
        </Route>
        <Route element={<Guard admin />}>
          <Route path="admin" element={<AdminLayout />}>
            <Route index element={<AdminHome />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="auctions" element={<AdminAuctions />} />
          </Route>
        </Route>
        <Route
          path="*"
          element={
            <Empty title="Không tìm thấy trang">
              <Link className="button" to="/">
                Về trang chủ
              </Link>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
