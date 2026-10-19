import {
  test,
  expect,
  type APIRequestContext,
  type Page,
} from "@playwright/test";
const base = process.env.E2E_API_URL || "http://localhost:8080";
const password = "Frontend123!";
async function call(
  request: APIRequestContext,
  path: string,
  method = "GET",
  body?: unknown,
  token?: string,
) {
  const response = await request.fetch(base + path, {
    method,
    data: body,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  expect(response.ok(), `${method} ${path}: ${response.status()}`).toBeTruthy();
  return response.status() === 204 ? null : response.json();
}
async function account(request: APIRequestContext) {
  const name =
    "e2e" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const user = await call(request, "/api/auth/register", "POST", {
    username: name,
    email: name + "@example.com",
    password,
  });
  const result = await call(request, "/api/auth/login", "POST", {
    login: name,
    password,
  });
  return { ...user, token: result.accessToken };
}
async function administrator(request: APIRequestContext) {
  return (
    await call(request, "/api/auth/login", "POST", {
      login: "admin",
      password: "Admin123!",
    })
  ).accessToken;
}
async function create(
  request: APIRequestContext,
  seller: { token: string },
  name: string,
  end = 600,
  start = -1,
) {
  return call(
    request,
    "/api/auctions",
    "POST",
    {
      name,
      description: "Thông tin món hàng thử nghiệm",
      condition: "USED",
      imageUrl: null,
      startingPrice: "100.00",
      minimumBidStep: "10.00",
      startingTime: new Date(Date.now() + start * 1000).toISOString(),
      endingTime: new Date(Date.now() + end * 1000).toISOString(),
    },
    seller.token,
  );
}
async function login(page: Page, name: string, pw = password) {
  await page.goto("/login");
  await page.getByLabel("Tên tài khoản hoặc email").fill(name);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(pw);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("button", { name: "Đăng xuất" })).toBeVisible();
}
async function bid(page: Page, value: string) {
  await page.getByLabel("Giá đặt", { exact: true }).fill(value);
  await page.getByRole("button", { name: "Đặt giá", exact: true }).click();
  await expect(page.getByLabel("Giá đặt", { exact: true })).toHaveValue("");
}
test("GUI register/create one-image auction/manual bids/final result", async ({
  page,
  request,
  browser,
}) => {
  const name = "seller" + Date.now().toString(36);
  await page.goto("/register");
  await page.getByLabel("Tên tài khoản", { exact: true }).fill(name);
  await page.getByLabel("Email", { exact: true }).fill(name + "@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "Đăng xuất" })).toBeVisible();
  await page.goto("/selling/auctions/new");
  const item = "GUI camera " + name;
  await page.getByLabel("Tên món hàng").fill(item);
  await page.getByLabel("Mô tả", { exact: true }).fill("Một món hàng, một ảnh");
  await page
    .getByLabel("URL ảnh", { exact: true })
    .fill("https://example.com/missing.jpg");
  await page.getByLabel("Giá khởi điểm").fill("100");
  await page.getByLabel("Bước giá").fill("10");
  const end = new Date(Date.now() + 35000);
  const local = new Date(end.getTime() - end.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 19);
  await page.getByLabel("Kết thúc", { exact: true }).fill(local);
  await page.getByRole("button", { name: "Tạo phiên", exact: true }).click();
  await expect(page).toHaveURL(/\/auctions\/\d+$/);
  const id = page.url().split("/").pop()!;
  await expect(
    page.getByRole("heading", { name: item, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Bạn không thể đặt giá phiên của mình."),
  ).toBeVisible();
  const user = await account(request);
  const rival = await account(request);
  const ctx = await browser.newContext();
  const bidder = await ctx.newPage();
  try {
    await login(bidder, user.username);
    await bidder.goto(`/auctions/${id}`);
    await bid(bidder, "100");
    await call(
      request,
      `/api/auctions/${id}/bids`,
      "POST",
      { amount: "110" },
      rival.token,
    );
    await expect(bidder.getByTestId("auction-price")).toHaveText("110 ₫");
    await bid(bidder, "120");
    await expect
      .poll(async () => (await call(request, `/api/auctions/${id}`)).status, {
        timeout: 50000,
      })
      .toBe("ENDED");
    await expect(
      bidder.getByText(`Người thắng: #${user.id}`, { exact: true }),
    ).toBeVisible();
    await expect(bidder.getByText("Giá cuối: 120 ₫")).toBeVisible();
    await bidder.goto("/activity");
    await expect(
      bidder.getByRole("cell", { name: "Thắng", exact: true }),
    ).toBeVisible();
  } finally {
    await ctx.close();
  }
});
test("realtime updates and a concurrent conflict retains input without replaying bid", async ({
  page,
  request,
}) => {
  const seller = await account(request),
    user = await account(request),
    rival = await account(request);
  const a = await create(request, seller, "Realtime refresh");
  const third = await account(request);
  await login(page, user.username);
  await page.goto(`/auctions/${a.id}`);
  await expect(page.getByTestId("auction-price")).toHaveText("100 ₫");
  await call(
    request,
    `/api/auctions/${a.id}/bids`,
    "POST",
    { amount: "110" },
    rival.token,
  );
  await expect(page.getByTestId("auction-price")).toHaveText("110 ₫");
  let posts = 0;
  await page.route(`**/api/auctions/${a.id}/bids`, async (route) => {
    if (route.request().method() === "POST") {
      posts++;
      await call(
        request,
        `/api/auctions/${a.id}/bids`,
        "POST",
        { amount: "150" },
        third.token,
      );
    }
    await route.continue();
  });
  await page.getByLabel("Giá đặt", { exact: true }).fill("120");
  await page.getByRole("button", { name: "Đặt giá", exact: true }).click();
  await expect(page.getByLabel("Giá đặt", { exact: true })).toHaveValue("120");
  await expect(page.getByText(/Minimum allowed bid/)).toBeVisible();
  await expect(page.getByTestId("auction-price")).toHaveText("150 ₫");
  expect(posts).toBe(1);
});
test("admin hard-delete user recalculates result and revokes token", async ({
  page,
  request,
}) => {
  const seller = await account(request),
    first = await account(request),
    deleted = await account(request);
  const a = await create(request, seller, "Recalculate ended", 2);
  const owned = await create(request, deleted, "Deleted seller auction");
  await call(
    request,
    `/api/auctions/${a.id}/bids`,
    "POST",
    { amount: "100" },
    first.token,
  );
  await call(
    request,
    `/api/auctions/${a.id}/bids`,
    "POST",
    { amount: "110" },
    deleted.token,
  );
  await expect
    .poll(async () => (await call(request, `/api/auctions/${a.id}`)).status, {
      timeout: 15000,
    })
    .toBe("ENDED");
  await login(page, "admin", "Admin123!");
  await page.goto("/admin/users");
  const row = page.getByRole("row").filter({
    has: page.getByRole("cell", { name: deleted.username, exact: true }),
  });
  await row.getByRole("button", { name: "Xóa tài khoản", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "kể cả phiên đã kết thúc",
  );
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(row).toHaveCount(0);
  const result = await call(request, `/api/auctions/${a.id}`);
  expect(result.winnerUserId).toBe(first.id);
  expect(result.finalPrice).toBe(100);
  expect((await request.get(`${base}/api/auctions/${owned.id}`)).status()).toBe(
    404,
  );
  expect(
    (
      await request.get(`${base}/api/auth/me`, {
        headers: { Authorization: `Bearer ${deleted.token}` },
      })
    ).status(),
  ).toBe(401);
});
test("admin hard-delete auction removes bid history; cannot delete self", async ({
  page,
  request,
}) => {
  const seller = await account(request),
    user = await account(request);
  const a = await create(request, seller, "Cleanup auction");
  await call(
    request,
    `/api/auctions/${a.id}/bids`,
    "POST",
    { amount: "100" },
    user.token,
  );
  await login(page, "admin", "Admin123!");
  await page.goto("/admin/auctions");
  const row = page.getByRole("row").filter({
    has: page.getByRole("link", {
      name: `#${a.id} · ${a.name}`,
      exact: true,
    }),
  });
  await row.getByRole("button", { name: "Xóa phiên", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(row).toHaveCount(0);
  expect((await request.get(`${base}/api/auctions/${a.id}`)).status()).toBe(
    404,
  );
  expect(
    (await call(request, "/api/users/me/bids", "GET", undefined, user.token))
      .totalElements,
  ).toBe(0);
  await page.goto("/admin/users");
  const adminRow = page
    .getByRole("row")
    .filter({ has: page.getByRole("cell", { name: "admin", exact: true }) });
  // The demo database survives runs; the seeded admin may be on a later page.
  await expect(page.getByRole("table")).toBeVisible();
  const next = page.getByRole("button", { name: "Tiếp", exact: true });
  while ((await adminRow.count()) === 0 && (await next.isEnabled())) {
    const response = page.waitForResponse(
      (r) =>
        r.url().includes("/api/admin/users?") && r.request().method() === "GET",
    );
    await next.click();
    await response;
    await expect(page.getByRole("table")).toBeVisible();
  }
  await expect(adminRow).toContainText("Đang đăng nhập");
  await expect(adminRow.getByRole("button")).toHaveCount(0);
});
test("seller deletion permissions and removed screens", async ({
  page,
  request,
}) => {
  const seller = await account(request);
  const a = await create(request, seller, "Owner cleanup");
  await login(page, seller.username);
  await page.goto("/selling");
  const row = page
    .getByRole("row")
    .filter({ has: page.getByRole("link", { name: a.name, exact: true }) });
  await row.getByRole("button", { name: "Xóa phiên", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(row).toHaveCount(0);
  await page.goto("/admin/users");
  await expect(page.getByText("Bạn không có quyền quản trị")).toBeVisible();
  for (const path of [
    "/products",
    "/profile",
    "/admin/categories",
    "/wallet",
  ]) {
    await page.goto(path);
    await expect(
      page.getByRole("heading", { name: "Không tìm thấy trang" }),
    ).toBeVisible();
  }
});
test("scheduled auction becomes failed without bids", async ({
  page,
  request,
}) => {
  const seller = await account(request);
  const a = await create(request, seller, "Scheduled no bids", 8, 2);
  await page.goto(`/auctions/${a.id}`);
  await expect(page.getByText("Phiên chưa bắt đầu.")).toBeVisible();
  await expect
    .poll(async () => (await call(request, `/api/auctions/${a.id}`)).status, {
      timeout: 20000,
    })
    .toBe("FAILED");
  await expect(
    page.getByText("Phiên kết thúc không có người thắng."),
  ).toBeVisible();
});
test("decimal precision and double-click produce one bid", async ({
  page,
  request,
}) => {
  const seller = await account(request),
    user = await account(request);
  const now = Date.now();
  const a = await call(
    request,
    "/api/auctions",
    "POST",
    {
      name: "Decimal auction",
      description: "Exact decimal",
      condition: "NEW",
      startingPrice: "0.10",
      minimumBidStep: "0.10",
      imageUrl: null,
      startingTime: new Date(now - 1000).toISOString(),
      endingTime: new Date(now + 600000).toISOString(),
    },
    seller.token,
  );
  await login(page, user.username);
  await page.goto(`/auctions/${a.id}`);
  let posts = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith(`/api/auctions/${a.id}/bids`))
      posts++;
  });
  await page.getByLabel("Giá đặt", { exact: true }).fill("0.10");
  await page.getByRole("button", { name: "Đặt giá", exact: true }).dblclick();
  await expect(page.getByTestId("auction-price")).toHaveText("0,10 ₫");
  await expect(page.getByLabel("Giá đặt", { exact: true })).toHaveValue("");
  expect(posts).toBe(1);
  expect(
    (await call(request, `/api/auctions/${a.id}/bids`)).totalElements,
  ).toBe(1);
});
test("SSE reaches a viewer connected to a different API instance", async ({
  page,
  request,
}) => {
  const replica = process.env.E2E_REPLICA_URL;
  test.skip(!replica, "Set E2E_REPLICA_URL to test a second API process");
  const eventsUrl = new URL("/api/events", replica!).toString();
  await page.addInitScript((url) => {
    const Original = window.EventSource;
    window.EventSource = class extends Original {
      constructor(_url: string | URL, options?: EventSourceInit) {
        super(url, options);
      }
    };
  }, eventsUrl);
  const seller = await account(request),
    user = await account(request);
  const a = await create(request, seller, "Cross-instance broadcast");
  await page.goto(`/auctions/${a.id}`);
  await expect(
    page.getByText("Đang cập nhật trực tiếp", { exact: true }),
  ).toBeVisible();
  await call(
    request,
    `/api/auctions/${a.id}/bids`,
    "POST",
    { amount: "125" },
    user.token,
  );
  await expect(page.getByTestId("auction-price")).toHaveText("125 ₫");
});

test("search pagination direct link and mobile layout", async ({
  page,
  request,
}) => {
  const seller = await account(request);
  const prefix = "Filter" + Date.now();
  for (let i = 0; i < 13; i++) await create(request, seller, `${prefix} ${i}`);
  await page.goto("/auctions");
  await page.getByLabel("Tìm tên món hàng").fill(prefix);
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.locator(".auction-card")).toHaveCount(12);
  await page.getByRole("button", { name: "Tiếp", exact: true }).click();
  await expect(page.locator(".auction-card")).toHaveCount(1);
  await page.locator(".auction-card").click();
  await page.reload();
  await expect(page.getByTestId("auction-price")).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Mở menu" }).click();
  await page.getByRole("button", { name: "Đóng menu" }).click();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBeTruthy();
});
