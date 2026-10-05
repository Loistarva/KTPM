import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, NoticeProvider, useAuth } from "../src/context";
import { Guard } from "../src/components";
import { getToken, setToken } from "../src/lib/api";
const profile = {
  id: 15,
  username: "tester",
  email: "tester@example.com",
  role: "USER",
  createdAt: "2026-10-05T00:00:00Z",
};
afterEach(() => {
  cleanup();
  setToken(null);
  vi.unstubAllGlobals();
});
function wrap(children: React.ReactNode, initial = "/private") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initial]}>
        <NoticeProvider>
          <AuthProvider>{children}</AuthProvider>
        </NoticeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
function LoginTarget() {
  const location = useLocation();
  return <p>Login {location.search}</p>;
}
function Probe() {
  const auth = useAuth();
  return (
    <>
      <p>{auth.loading ? "restoring" : auth.user?.username || "guest"}</p>
      <button onClick={() => void auth.logout().catch(() => {})}>logout</button>
      <button onClick={() => void auth.login("new-user", "Frontend123!")}>
        login-new
      </button>
    </>
  );
}
describe("Authentication and cache boundaries", () => {
  it("holds private route until profile restore completes", async () => {
    setToken("test-token");
    let finish!: (r: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        () =>
          new Promise((resolve) => {
            finish = resolve;
          }),
      ),
    );
    wrap(
      <Routes>
        <Route element={<Guard />}>
          <Route path="private" element={<p>private content</p>} />
        </Route>
      </Routes>,
    );
    expect(screen.queryByText("private content")).toBeNull();
    expect(screen.getByRole("status")).toBeTruthy();
    finish(new Response(JSON.stringify(profile)));
    await screen.findByText("private content");
  });
  it("redirects guest to login with safe internal return path", async () => {
    wrap(
      <Routes>
        <Route element={<Guard />}>
          <Route path="private" element={<p>private content</p>} />
        </Route>
        <Route path="login" element={<LoginTarget />} />
      </Routes>,
    );
    await screen.findByText("Login ?returnTo=%2Fprivate");
  });
  it("denies USER direct admin route without loading admin data", async () => {
    setToken("test-token");
    const fetcher = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify(profile))),
      );
    vi.stubGlobal("fetch", fetcher);
    wrap(
      <Routes>
        <Route element={<Guard admin />}>
          <Route path="private" element={<p>admin content</p>} />
        </Route>
      </Routes>,
    );
    await screen.findByText("Bạn không có quyền quản trị");
    expect(screen.queryByText("admin content")).toBeNull();
    expect(fetcher.mock.calls.every(([url]) => url === "/api/auth/me")).toBe(
      true,
    );
  });
  it("clears bid cache and local session even when server logout fails", async () => {
    setToken("test-token");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation((url: string) =>
          url.endsWith("/logout")
            ? Promise.reject(new TypeError("network"))
            : Promise.resolve(new Response(JSON.stringify(profile))),
        ),
    );
    const client = wrap(<Probe />);
    await screen.findByText("tester");
    client.setQueryData(["/api/users/me/bids"], {
      totalElements: "private-history",
    });
    await userEvent.click(screen.getByRole("button", { name: "logout" }));
    await screen.findByText("guest");
    expect(getToken()).toBeNull();
    expect(client.getQueryData(["/api/users/me/bids"])).toBeUndefined();
  });
  it("clears prior identity cache when logging into another account", async () => {
    setToken("old-token");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) =>
        Promise.resolve(
          new Response(
            JSON.stringify(
              url.endsWith("/login")
                ? {
                    accessToken: "new-token",
                    expiresIn: 3600,
                    tokenType: "Bearer",
                  }
                : {
                    ...profile,
                    username:
                      getToken() === "new-token" ? "new-user" : "tester",
                  },
            ),
          ),
        ),
      ),
    );
    const client = wrap(<Probe />);
    await screen.findByText("tester");
    client.setQueryData(["/api/users/me/bids"], { totalElements: 999 });
    await userEvent.click(screen.getByRole("button", { name: "login-new" }));
    await screen.findByText("new-user");
    await waitFor(() =>
      expect(client.getQueryData(["/api/users/me/bids"])).toBeUndefined(),
    );
  });
});
