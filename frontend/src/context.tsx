import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, getToken, setToken } from "./lib/api";
import type { Profile, Token } from "./types";
type Auth = {
  user: Profile | null;
  loading: boolean;
  error: string;
  login: (login: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
};
const AuthContext = createContext<Auth>(null!);
export const useAuth = () => useContext(AuthContext);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(!!getToken());
  const [error, setError] = useState("");
  const client = useQueryClient();
  const generation = useRef(0);
  const clear = () => {
    generation.current++;
    setToken(null);
    setUser(null);
    setError("");
    setLoading(false);
    client.clear();
  };
  const reload = async () => {
    const original = getToken();
    const gen = generation.current;
    if (!original) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const profile = await api<Profile>("/api/auth/me");
      if (gen === generation.current && original === getToken())
        setUser(profile);
    } catch (e) {
      if (gen === generation.current && original === getToken())
        setError((e as Error).message);
    } finally {
      if (gen === generation.current) setLoading(false);
    }
  };
  useEffect(() => {
    void reload();
    const expired = (e: Event) => {
      if ((e as CustomEvent).detail === getToken()) clear();
    };
    window.addEventListener("ktpm-unauthorized", expired);
    return () => window.removeEventListener("ktpm-unauthorized", expired);
  }, []);
  const login = async (loginValue: string, password: string) => {
    generation.current++;
    client.clear();
    const result = await api<Token>("/api/auth/login", {
      method: "POST",
      body: { login: loginValue, password },
      auth: false,
    });
    setToken(result.accessToken);
    try {
      const profile = await api<Profile>("/api/auth/me");
      setUser(profile);
      setError("");
    } catch (e) {
      clear();
      throw e;
    }
  };
  const logout = async () => {
    let failure: unknown;
    try {
      await api("/api/auth/logout", { method: "POST" });
    } catch (e) {
      failure = e;
    } finally {
      clear();
    }
    if (failure)
      throw new Error(
        "Đã đăng xuất trên thiết bị; chưa xác nhận thu hồi phiên ở server vì mất kết nối.",
      );
  };
  return (
    <AuthContext.Provider
      value={{ user, loading, error, login, logout, reload }}
    >
      {children}
    </AuthContext.Provider>
  );
}
type Notice = { id: number; message: string; error: boolean };
const NoticeContext = createContext<(message: string, error?: boolean) => void>(
  () => {},
);
export const useNotice = () => useContext(NoticeContext);
export function NoticeProvider({ children }: { children: ReactNode }) {
  const [notices, setNotices] = useState<Notice[]>([]);
  const next = useRef(0);
  const notify = (message: string, error = false) => {
    const id = ++next.current;
    setNotices((v) => [...v, { id, message, error }]);
    window.setTimeout(
      () => setNotices((v) => v.filter((n) => n.id !== id)),
      6500,
    );
  };
  return (
    <NoticeContext.Provider value={notify}>
      {children}
      <div className="notices" aria-live="polite">
        {notices.map((n) => (
          <div key={n.id} className={"notice " + (n.error ? "danger" : "")}>
            {n.message}
            <button
              aria-label="Đóng thông báo"
              onClick={() => setNotices((v) => v.filter((x) => x.id !== n.id))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </NoticeContext.Provider>
  );
}
