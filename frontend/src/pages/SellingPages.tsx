import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useApi, useAction } from "../lib/queries";
import { api } from "../lib/api";
import {
  Heading,
  Field,
  ErrorBox,
  Loading,
  Empty,
  Pager,
  Status,
  Confirm,
} from "../components";
import {
  money,
  date,
  validateMoney,
  localDateInput,
  localToUtc,
} from "../lib/money";
import type { Auction, Page } from "../types";
export function SellingPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const data = useApi<Page<Auction>>(`/api/auctions/me?page=${page}&size=20`);
  const remove = useAction<string>(
    (id) => api(`/api/auctions/${id}`, { method: "DELETE" }),
    "Đã xóa phiên.",
  );
  return (
    <>
      <Heading
        title="Phiên của tôi"
        description="Bạn có thể xóa phiên chưa có lượt đặt giá."
      >
        <Link className="button" to="/selling/auctions/new">
          Tạo phiên đấu giá
        </Link>
        <button
          className="button secondary"
          onClick={() => void data.refetch()}
        >
          Làm mới
        </button>
      </Heading>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorBox error={data.error} />
      ) : !data.data?.content.length ? (
        <Empty title="Bạn chưa có phiên nào" />
      ) : (
        <div className="table-wrap panel">
          <table>
            <thead>
              <tr>
                <th>Món hàng</th>
                <th>Giá</th>
                <th>Trạng thái</th>
                <th>Kết thúc</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {data.data.content.map((a) => (
                <tr key={String(a.id)}>
                  <td>
                    <Link to={`/auctions/${a.id}`}>{a.name}</Link>
                  </td>
                  <td>{money(a.currentPrice)}</td>
                  <td>
                    <Status value={a.status} />
                  </td>
                  <td>{date(a.endingTime)}</td>
                  <td>
                    {a.winningBidId == null && (
                      <Confirm
                        title="Xóa phiên đấu giá?"
                        trigger="Xóa phiên"
                        danger
                        pending={remove.isPending}
                        onConfirm={() => remove.mutateAsync(String(a.id))}
                      >
                        Phiên và thông tin món hàng sẽ bị xóa hẳn.
                      </Confirm>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager
        data={data.data}
        onChange={(p) => setParams({ page: String(p) })}
      />
    </>
  );
}
export function AuctionFormPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [values, setValues] = useState({
    name: "",
    description: "",
    condition: "USED",
    imageUrl: "",
    startingPrice: "",
    minimumBidStep: "",
    startingTime: localDateInput(new Date()),
    endingTime: localDateInput(new Date(Date.now() + 3600000)),
  });
  const create = useAction<typeof values>(async (v) => {
    const result = await api<Auction>("/api/auctions", {
      method: "POST",
      body: {
        ...v,
        imageUrl: v.imageUrl.trim() || null,
        startingPrice: v.startingPrice.trim(),
        minimumBidStep: v.minimumBidStep.trim(),
        startingTime: localToUtc(v.startingTime),
        endingTime: localToUtc(v.endingTime),
      },
    });
    navigate(`/auctions/${result.id}`);
  }, "Đã tạo phiên.");
  const change =
    (key: keyof typeof values) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
      >,
    ) =>
      setValues((v) => ({ ...v, [key]: e.target.value }));
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (create.isPending) return;
    const problem =
      validateMoney(values.startingPrice) ||
      validateMoney(values.minimumBidStep);
    if (problem) {
      setError(problem);
      return;
    }
    if (
      Date.parse(values.endingTime) <= Date.parse(values.startingTime) ||
      Date.parse(values.endingTime) <= Date.now()
    ) {
      setError("Kết thúc phải sau bắt đầu và ở tương lai.");
      return;
    }
    setError("");
    try {
      await create.mutateAsync(values);
    } catch {}
  }
  return (
    <>
      <Heading
        title="Tạo phiên đấu giá"
        description="Nhập thông tin món hàng và giá trong một lần."
      />
      <form className="panel auction-form" onSubmit={submit}>
        <Field label="Tên món hàng">
          <input
            required
            maxLength={200}
            value={values.name}
            onChange={change("name")}
          />
        </Field>
        <Field label="Mô tả">
          <textarea
            required
            maxLength={5000}
            value={values.description}
            onChange={change("description")}
          />
        </Field>
        <Field label="Tình trạng">
          <select value={values.condition} onChange={change("condition")}>
            <option value="USED">Đã sử dụng</option>
            <option value="NEW">Mới</option>
          </select>
        </Field>
        <Field
          label="URL ảnh"
          hint="Một ảnh tùy chọn, địa chỉ http:// hoặc https://."
        >
          <input
            type="url"
            pattern="https?://.+"
            maxLength={2000}
            value={values.imageUrl}
            onChange={change("imageUrl")}
          />
        </Field>
        <div className="form-grid">
          <Field label="Giá khởi điểm">
            <input
              required
              inputMode="decimal"
              value={values.startingPrice}
              onChange={change("startingPrice")}
            />
          </Field>
          <Field label="Bước giá">
            <input
              required
              inputMode="decimal"
              value={values.minimumBidStep}
              onChange={change("minimumBidStep")}
            />
          </Field>
          <Field label="Bắt đầu">
            <input
              required
              type="datetime-local"
              step="1"
              value={values.startingTime}
              onChange={change("startingTime")}
            />
          </Field>
          <Field label="Kết thúc">
            <input
              required
              type="datetime-local"
              step="1"
              value={values.endingTime}
              onChange={change("endingTime")}
            />
          </Field>
        </div>
        {error && <ErrorBox error={error} />}
        <button className="button" disabled={create.isPending}>
          {create.isPending ? "Đang tạo…" : "Tạo phiên"}
        </button>
      </form>
    </>
  );
}
