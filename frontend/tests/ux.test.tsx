import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, NoticeProvider } from "../src/context";
import { Field, Layout } from "../src/components";
import { AuthPage } from "../src/pages/AuthPages";
import { ExplorePage } from "../src/pages/ExplorePage";
import { AuctionPage } from "../src/pages/AuctionPage";
import { ErrorBoundary } from "../src/App";
import { setToken } from "../src/lib/api";
import type { Auction } from "../src/types";

const auction: Auction = {
  id: 7,
  sellerId: 1,
  name: "Máy ảnh thử nghiệm",
  description: "Thông tin sản phẩm",
  condition: "USED",
  imageUrl: null,
  startingPrice: "100",
  currentPrice: "100.10",
  minimumBidStep: "0.10",
  finalPrice: null,
  status: "ACTIVE",
  winnerUserId: 3,
  winningBidId: 8,
  startingTime: "2026-01-01T00:00:00Z",
  endingTime: "2099-01-01T00:00:00Z",
  createdAt: "2026-01-01T00:00:00Z",
};
const profile = {
  id: 2,
  username: "bidder",
  email: "bidder@example.com",
  role: "USER",
  createdAt: "2026-01-01T00:00:00Z",
};
const page = {
  content: [auction],
  page: 0,
  size: 12,
  totalElements: 1,
  totalPages: 1,
};
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  clients.forEach((client) => client.clear());
  clients.length = 0;
  setToken(null);
  vi.unstubAllGlobals();
});
function wrap(children: React.ReactNode, entries = ["/auctions"]) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  clients.push(client);
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={entries}>
        <NoticeProvider>
          <AuthProvider>{children}</AuthProvider>
        </NoticeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
function Navigation() {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate(-1)}>Quay lại trước</button>
      <button onClick={() => navigate("/auctions/9")}>Phiên tiếp theo</button>
    </>
  );
}
function fakeServer() {
  const writes: unknown[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options?: RequestInit) => {
      if (options?.method === "POST") writes.push(options.body);
      const body =
        url === "/api/auth/me"
          ? profile
          : /\/bids\?/.test(url)
            ? { ...page, content: [], totalElements: 0 }
            : /\/api\/auctions\/\d+$/.test(url)
              ? { ...auction, id: url.endsWith("9") ? 9 : 7 }
              : page;
      return new Response(JSON.stringify(body), {
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
  return writes;
}

it("restores the search input when browser history changes the applied query", async () => {
  fakeServer();
  wrap(
    <>
      <Navigation />
      <ExplorePage />
    </>,
    ["/auctions?search=mayanh", "/auctions?search=dongho"],
  );
  expect(
    (
      screen.getByRole("textbox", {
        name: "Tìm tên món hàng",
      }) as HTMLInputElement
    ).value,
  ).toBe("dongho");
  await userEvent.click(screen.getByRole("button", { name: "Quay lại trước" }));
  await waitFor(() =>
    expect(
      (
        screen.getByRole("textbox", {
          name: "Tìm tên món hàng",
        }) as HTMLInputElement
      ).value,
    ).toBe("mayanh"),
  );
});

it("clears all active filters even when the result list is not empty", async () => {
  fakeServer();
  wrap(<ExplorePage />, ["/auctions?status=ACTIVE&search=camera"]);
  await screen.findByText("Máy ảnh thử nghiệm");
  await userEvent.click(
    screen.getByRole("button", { name: "Xóa tất cả bộ lọc" }),
  );
  expect(
    (
      screen.getByRole("textbox", {
        name: "Tìm tên món hàng",
      }) as HTMLInputElement
    ).value,
  ).toBe("");
  expect(
    screen
      .getByRole("button", { name: "Tất cả phiên" })
      .getAttribute("aria-pressed"),
  ).toBe("true");
});

it("connects a field's help and validation error to its input for assistive technology", () => {
  render(
    <Field
      label="Giá đặt"
      hint="Dùng dấu chấm cho phần thập phân"
      error="Giá chưa hợp lệ"
    >
      <input />
    </Field>,
  );
  const input = screen.getByRole("textbox", { name: "Giá đặt" });
  const ids = input.getAttribute("aria-describedby")?.split(" ") || [];
  expect(
    ids.map((id) => document.getElementById(id)?.textContent).join(" "),
  ).toContain("Dùng dấu chấm cho phần thập phân");
  expect(
    ids.map((id) => document.getElementById(id)?.textContent).join(" "),
  ).toContain("Giá chưa hợp lệ");
  expect(input.getAttribute("aria-invalid")).toBe("true");
});

it("shows and hides the entered password without losing its value or submitting", async () => {
  fakeServer();
  wrap(<AuthPage />, ["/login"]);
  const input = screen.getByLabelText("Mật khẩu", {
    exact: true,
  }) as HTMLInputElement;
  await userEvent.type(input, "Example123!");
  await userEvent.click(screen.getByRole("button", { name: "Hiện mật khẩu" }));
  expect(input.type).toBe("text");
  expect(input.value).toBe("Example123!");
  await userEvent.click(screen.getByRole("button", { name: "Ẩn mật khẩu" }));
  expect(input.type).toBe("password");
  expect(input.value).toBe("Example123!");
});

it("closes the mobile navigation with Escape", async () => {
  wrap(
    <Routes>
      <Route element={<Layout />}>
        <Route path="auctions" element={<p>Danh sách phiên</p>} />
      </Route>
    </Routes>,
  );
  await userEvent.click(screen.getByRole("button", { name: "Mở menu" }));
  expect(
    screen
      .getByRole("button", { name: "Đóng menu" })
      .getAttribute("aria-expanded"),
  ).toBe("true");
  await userEvent.keyboard("{Escape}");
  expect(
    screen
      .getByRole("button", { name: "Mở menu" })
      .getAttribute("aria-expanded"),
  ).toBe("false");
});

it("previews the exact decimal bid selected by a suggestion without placing it", async () => {
  const writes = fakeServer();
  setToken("test-token");
  wrap(
    <Routes>
      <Route path="auctions/:id" element={<AuctionPage />} />
    </Routes>,
    ["/auctions/7"],
  );
  await userEvent.click(
    await screen.findByRole("button", { name: "Giá tối thiểu" }),
  );
  expect(
    (screen.getByLabelText("Giá đặt", { exact: true }) as HTMLInputElement)
      .value,
  ).toBe("100.2");
  expect(
    screen.getByRole("status", { name: "Mức giá chuẩn bị gửi" }).textContent,
  ).toContain("100,20 ₫");
  expect(writes).toHaveLength(0);
});

it("does not carry an unfinished bid into another auction", async () => {
  fakeServer();
  setToken("test-token");
  wrap(
    <>
      <Navigation />
      <Routes>
        <Route path="auctions/:id" element={<AuctionPage />} />
      </Routes>
    </>,
    ["/auctions/7"],
  );
  await userEvent.type(
    await screen.findByLabelText("Giá đặt", { exact: true }),
    "150",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Phiên tiếp theo" }),
  );
  await waitFor(() =>
    expect(
      (screen.getByLabelText("Giá đặt", { exact: true }) as HTMLInputElement)
        .value,
    ).toBe(""),
  );
});

it.each([" 150", "150 "])(
  "previews and submits a pasted amount with surrounding whitespace: %j",
  async (value) => {
    const writes = fakeServer();
    setToken("test-token");
    wrap(
      <ErrorBoundary>
        <Routes>
          <Route path="auctions/:id" element={<AuctionPage />} />
        </Routes>
      </ErrorBoundary>,
      ["/auctions/7"],
    );
    await userEvent.type(
      await screen.findByLabelText("Giá đặt", { exact: true }),
      value,
    );
    expect(
      screen.getByRole("status", { name: "Mức giá chuẩn bị gửi" }).textContent,
    ).toContain("150 ₫");
    await userEvent.click(
      screen.getByRole("button", { name: "Đặt giá", exact: true }),
    );
    await waitFor(() =>
      expect(
        (screen.getByLabelText("Giá đặt", { exact: true }) as HTMLInputElement)
          .value,
      ).toBe(""),
    );
    expect(writes).toHaveLength(1);
    expect(JSON.parse(String(writes[0]))).toEqual({ amount: "150" });
  },
);
