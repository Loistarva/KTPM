import { act, cleanup, render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RealtimeProvider } from "../src/lib/realtime";

class FakeEvents extends EventTarget {
  static instance: FakeEvents;
  onerror?: () => void;
  close = vi.fn();
  constructor() {
    super();
    FakeEvents.instance = this;
  }
  emit(name: string, data: unknown = {}) {
    this.dispatchEvent(new MessageEvent(name, { data: JSON.stringify(data) }));
  }
}

describe("realtime synchronization", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("EventSource", FakeEvents);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });
  function setup() {
    const client = new QueryClient();
    for (const path of [
      "/api/auctions/1",
      "/api/auctions/2",
      "/api/auctions/1/bids?page=0",
      "/api/auctions?page=0",
      "/api/users/me/bids",
      "/api/auth/me",
    ])
      client.setQueryData([path], { test: true });
    const view = render(
      <QueryClientProvider client={client}>
        <RealtimeProvider>
          <span>app</span>
        </RealtimeProvider>
      </QueryClientProvider>,
    );
    return { client, view, events: FakeEvents.instance };
  }
  const invalid = (client: QueryClient, path: string) =>
    client.getQueryState([path])?.isInvalidated;

  it("coalesces events and invalidates only affected details plus shared lists", async () => {
    const { client, events } = setup();
    const refresh = vi.spyOn(client, "invalidateQueries");
    await act(async () => {
      for (let i = 0; i < 100; i++)
        events.emit("auction-changed", { auctionId: "1" });
      await vi.advanceTimersByTimeAsync(150);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(invalid(client, "/api/auctions/1")).toBe(true);
    expect(invalid(client, "/api/auctions/1/bids?page=0")).toBe(true);
    expect(invalid(client, "/api/auctions?page=0")).toBe(true);
    expect(invalid(client, "/api/auctions/2")).toBe(false);
    expect(invalid(client, "/api/auth/me")).toBe(false);
  });

  it("refreshes canonical state on reconnect and polls during an outage", async () => {
    const { client, events } = setup();
    const refresh = vi.spyOn(client, "invalidateQueries");
    await act(async () => {
      events.emit("resync");
      await vi.advanceTimersByTimeAsync(150);
    });
    expect(invalid(client, "/api/auctions/2")).toBe(true);
    refresh.mockClear();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(refresh).not.toHaveBeenCalled();
    await act(async () => {
      events.onerror?.();
      await vi.advanceTimersByTimeAsync(10150);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("cleans up the stream, refresh timer and fallback polling on unmount", async () => {
    const { client, events, view } = setup();
    const refresh = vi.spyOn(client, "invalidateQueries");
    act(() => events.emit("auction-changed", { auctionId: "1" }));
    view.unmount();
    await vi.advanceTimersByTimeAsync(20000);
    expect(events.close).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();
  });
});
