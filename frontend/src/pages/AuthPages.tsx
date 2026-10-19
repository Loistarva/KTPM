import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowRight, Gavel, ShieldCheck, Sparkles } from "lucide-react";
import { api } from "../lib/api";
import { useAuth } from "../context";
import { Field, ErrorBox } from "../components";
export function AuthPage({ register = false }: { register?: boolean }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [values, setValues] = useState({
    login: "",
    username: "",
    email: "",
    password: "",
  });
  const change =
    (name: keyof typeof values) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setValues((v) => ({ ...v, [name]: e.target.value }));
  const returnTo = params.get("returnTo") || "/";
  const safeReturn =
    returnTo.startsWith("/") &&
    !returnTo.startsWith("//") &&
    !returnTo.includes("\\") &&
    !/^\/(login|register)/.test(returnTo)
      ? returnTo
      : "/";
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (pending) return;
    setError("");
    if (new TextEncoder().encode(values.password).length > 72) {
      setError("Mật khẩu tối đa 72 byte UTF-8.");
      return;
    }
    setPending(true);
    try {
      if (register) {
        await api("/api/auth/register", {
          method: "POST",
          auth: false,
          body: {
            username: values.username,
            email: values.email,
            password: values.password,
          },
        });
      }
      await auth.login(
        register ? values.username : values.login,
        values.password,
      );
      navigate(safeReturn, { replace: true });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="auth-shell">
      <aside className="auth-story">
        <span className="auth-brand-icon">
          <Gavel size={25} />
        </span>
        <p className="eyebrow">KTPM AUCTION</p>
        <h1>
          Tìm món đồ mình thích.
          <br />
          Chọn mức giá của mình.
        </h1>
        <p>
          Một cộng đồng, nhiều cơ hội mới. Bắt đầu hành trình khám phá và đấu
          giá cùng KTPM.
        </p>
        <div className="story-note">
          <Sparkles size={18} />
          <div>
            <strong>Một tài khoản, mọi cơ hội.</strong>
            <span>Vừa khám phá, vừa bán những món đồ của bạn.</span>
          </div>
        </div>
      </aside>
      <section className="panel auth-form">
        <p className="eyebrow">
          {register ? "BẮT ĐẦU CÂU CHUYỆN CỦA BẠN" : "RẤT VUI ĐƯỢC GẶP LẠI BẠN"}
        </p>
        <h2>{register ? "Tạo tài khoản" : "Chào mừng trở lại."}</h2>
        <p className="muted">
          {register
            ? "Bắt đầu hành trình đấu giá của bạn."
            : "Tiếp tục khám phá những phiên đấu giá."}
        </p>
        <form onSubmit={submit}>
          {register ? (
            <>
              <Field
                label="Tên tài khoản"
                hint="3–50 ký tự: chữ, số hoặc dấu gạch dưới."
              >
                <input
                  value={values.username}
                  onChange={change("username")}
                  required
                  pattern="[a-zA-Z0-9_]{3,50}"
                  maxLength={50}
                  autoComplete="username"
                  placeholder="Tên tài khoản của bạn"
                />
              </Field>
              <Field label="Email">
                <input
                  type="email"
                  value={values.email}
                  onChange={change("email")}
                  required
                  maxLength={254}
                  autoComplete="email"
                  placeholder="ban@example.com"
                />
              </Field>
            </>
          ) : (
            <Field label="Tên tài khoản hoặc email">
              <input
                value={values.login}
                onChange={change("login")}
                required
                maxLength={254}
                autoComplete="username"
                placeholder="Nhập tài khoản hoặc email"
              />
            </Field>
          )}
          <Field
            label="Mật khẩu"
            hint={register ? "Từ 8 ký tự, tối đa 72 byte UTF-8." : undefined}
          >
            <input
              type="password"
              value={values.password}
              onChange={change("password")}
              required
              minLength={register ? 8 : undefined}
              maxLength={72}
              autoComplete={register ? "new-password" : "current-password"}
              placeholder={register ? "Tạo mật khẩu của bạn" : "Nhập mật khẩu"}
            />
          </Field>
          {error && <ErrorBox error={error} />}
          <button className="button full" disabled={pending}>
            {pending ? "Đang xử lý…" : register ? "Tạo tài khoản" : "Đăng nhập"}
            <ArrowRight size={17} />
          </button>
        </form>
        <p className="auth-switch">
          {register ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
          <Link
            to={
              (register ? "/login" : "/register") +
              "?returnTo=" +
              encodeURIComponent(safeReturn)
            }
          >
            {register ? "Đăng nhập" : "Đăng ký ngay"}
          </Link>
        </p>
        <p className="auth-assurance">
          <ShieldCheck size={14} /> Tài khoản của bạn được bảo vệ và xác thực.
        </p>
      </section>
    </div>
  );
}
