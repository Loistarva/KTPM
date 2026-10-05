import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, queryString, setToken } from "../src/lib/api";
afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});
describe("API transport", () => {
  it("accepts 204 with no body and sends authorization", async () => {
    setToken("token-one");
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    expect(await api("/api/auth/logout", { method: "POST" })).toBeUndefined();
    expect(fetcher.mock.calls[0][1].headers.Authorization).toBe(
      "Bearer token-one",
    );
  });
  it("shows backend JSON error without retrying a mutation", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ message: "Minimum allowed bid is 110000.00" }),
          { status: 409 },
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    await expect(
      api("/api/bids", { method: "POST", body: { amount: "100" } }),
    ).rejects.toThrow("Minimum allowed bid");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("handles a proxy HTML error", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("<html>bad gateway</html>", { status: 502 }),
        ),
    );
    await expect(api("/api/test")).rejects.toMatchObject({ status: 502 });
  });
  it("handles malformed success JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("<html/>", { status: 200 })),
    );
    await expect(api("/api/test")).rejects.toBeInstanceOf(ApiError);
  });
  it("does not revoke a newer login on stale 401", async () => {
    let finish!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        () =>
          new Promise((resolve) => {
            finish = resolve;
          }),
      ),
    );
    const listener = vi.fn();
    window.addEventListener("ktpm-unauthorized", listener);
    setToken("old");
    const request = api("/api/users/me");
    setToken("new");
    finish(new Response("{}", { status: 401 }));
    await expect(request).rejects.toMatchObject({ status: 401 });
    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener("ktpm-unauthorized", listener);
  });
  it("emits expiry for the current token but not public login failures", async () => {
    const listener = vi.fn();
    window.addEventListener("ktpm-unauthorized", listener);
    setToken("current");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve(new Response("{}", { status: 401 })),
        ),
    );
    await expect(api("/api/users/me")).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledTimes(1);
    await expect(api("/api/auth/login", { auth: false })).rejects.toMatchObject(
      { status: 401 },
    );
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener("ktpm-unauthorized", listener);
  });
  it("reports network failure and preserves abort semantics", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("Failed to fetch")),
    );
    await expect(api("/api/test")).rejects.toMatchObject({ status: 0 });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new DOMException("aborted", "AbortError")),
    );
    await expect(api("/api/test")).rejects.toMatchObject({
      name: "AbortError",
    });
  });
  it("omits empty filters, URL encodes search", () => {
    expect(queryString({ status: "", page: 0, search: "bàn & ghế" })).toBe(
      "?page=0&search=b%C3%A0n+%26+gh%E1%BA%BF",
    );
  });
});
