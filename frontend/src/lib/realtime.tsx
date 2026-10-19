import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";

const RealtimeContext = createContext(false);
export const useRealtimeStatus = () => useContext(RealtimeContext);

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const [connected, setConnected] = useState(false);
  useEffect(() => {
    let online = false;
    let refreshAll = false;
    let pending: ReturnType<typeof setTimeout> | undefined;
    const auctions = new Set<string>();
    const flush = () => {
      pending = undefined;
      const ids = new Set(auctions);
      const all = refreshAll;
      auctions.clear();
      refreshAll = false;
      void client.invalidateQueries({
        predicate: (query) => {
          const path = String(query.queryKey[0]);
          if (
            path.startsWith("/api/users/me/bids") ||
            path.startsWith("/api/admin/auctions")
          )
            return true;
          if (!path.startsWith("/api/auctions")) return false;
          const match = path.match(/^\/api\/auctions\/(\d+)(?:[/?]|$)/);
          return all || !match || ids.has(match[1]);
        },
      });
    };
    // Coalesce bursts without postponing refresh indefinitely under sustained bidding.
    const refresh = (id?: string) => {
      if (id) auctions.add(id);
      else refreshAll = true;
      pending ??= setTimeout(flush, 150);
    };
    const status = (value: boolean) => {
      online = value;
      setConnected(value);
    };
    const events = new EventSource(
      (import.meta.env.VITE_API_BASE_URL || "") + "/api/events",
    );
    events.addEventListener("resync", () => {
      status(true);
      // Reconnect can land on any backend. Fetch current state rather than relying on event order.
      refresh();
    });
    events.addEventListener("auction-changed", (event) => {
      try {
        const id = JSON.parse((event as MessageEvent).data).auctionId;
        if (typeof id === "string" && /^\d+$/.test(id)) refresh(id);
      } catch {
        refresh();
      }
    });
    events.addEventListener("degraded", () => {
      status(false);
      refresh();
    });
    events.addEventListener("heartbeat", (event) => {
      try {
        status(JSON.parse((event as MessageEvent).data).online === true);
      } catch {
        status(false);
      }
    });
    events.onerror = () => status(false);
    const recover = () => refresh();
    window.addEventListener("focus", recover);
    window.addEventListener("online", recover);
    const fallback = setInterval(() => {
      if (!online) refresh();
    }, 10000);
    return () => {
      events.close();
      clearTimeout(pending);
      clearInterval(fallback);
      window.removeEventListener("focus", recover);
      window.removeEventListener("online", recover);
    };
  }, [client]);
  return (
    <RealtimeContext.Provider value={connected}>
      {children}
    </RealtimeContext.Provider>
  );
}
