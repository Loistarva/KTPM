import { test, expect } from "@playwright/test";

test("mobile bidder reaches the form, reviews an exact amount and submits only once", async ({
  page,
  request,
}) => {
  const api = process.env.E2E_API_URL || "http://localhost:8080";
  const login = await request.post(`${api}/api/auth/login`, {
    data: { login: "seller", password: "Seller123!" },
  });
  expect(login.ok()).toBeTruthy();
  const token = (await login.json()).accessToken;
  const created = await request.post(`${api}/api/auctions`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: `UX camera ${Date.now()}`,
      description:
        "Một món hàng dùng riêng để kiểm chứng luồng đặt giá trên điện thoại.",
      condition: "USED",
      imageUrl: `${process.env.E2E_FRONTEND_URL || "http://localhost:5173"}/images/auction-editorial.webp`,
      startingPrice: "100.10",
      minimumBidStep: "0.10",
      startingTime: new Date(Date.now() - 1000).toISOString(),
      endingTime: new Date(Date.now() + 600000).toISOString(),
    },
  });
  expect(created.ok()).toBeTruthy();
  const auction = await created.json();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/login?returnTo=/auctions/${auction.id}`);
  await page.getByLabel("Tên tài khoản hoặc email").fill("bidder1");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Bidder123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  const input = page.getByLabel("Giá đặt", { exact: true });
  await expect(input).toBeVisible();
  await page.getByRole("link", { name: "Đến phần đặt giá" }).click();
  await expect(input).toBeInViewport({ ratio: 1 });
  await page
    .getByRole("button", { name: "Giá tối thiểu", exact: true })
    .click();
  await expect(
    page.getByRole("status", { name: "Mức giá chuẩn bị gửi" }),
  ).toContainText("100,10 ₫");
  await input.fill(" 100.10 ");
  await expect(
    page.getByRole("status", { name: "Mức giá chuẩn bị gửi" }),
  ).toContainText("100,10 ₫");
  await expect(
    page.getByRole("button", { name: "Đặt giá", exact: true }),
  ).toBeInViewport({ ratio: 1 });
  const before = await request.get(`${api}/api/auctions/${auction.id}/bids`);
  expect((await before.json()).totalElements).toBe(0);
  await page.getByRole("button", { name: "Đặt giá", exact: true }).click();
  await expect(input).toHaveValue("");
  await expect(page.locator(".leader-note")).toBeVisible();
  await page.getByRole("link", { name: "Xem lịch sử", exact: true }).click();
  await expect(page.locator("#bid-history")).toBeInViewport();
  const after = await request.get(`${api}/api/auctions/${auction.id}/bids`);
  expect((await after.json()).totalElements).toBe(1);
  for (const width of [390, 320, 300]) {
    await page.setViewportSize({ width, height: 844 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBeTruthy();
  }
});
