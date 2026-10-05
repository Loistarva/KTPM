import { parse } from "lossless-json";
let token: string | null = sessionStorage.getItem("ktpm-token");
export const getToken = () => token;
export function setToken(value: string | null) {
  token = value;
  if (value) sessionStorage.setItem("ktpm-token", value);
  else sessionStorage.removeItem("ktpm-token");
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export function parseJson(text: string): unknown {
  return parse(text, undefined, (value: string) => {
    const n = Number(value);
    return Number.isSafeInteger(n) &&
      !value.includes(".") &&
      !/[eE]/.test(value)
      ? n
      : value;
  });
}
export async function api<T>(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    signal?: AbortSignal;
    auth?: boolean;
  } = {},
): Promise<T> {
  const requestToken = options.auth === false ? null : token;
  let response: Response;
  try {
    response = await fetch((import.meta.env.VITE_API_BASE_URL || "") + path, {
      method: options.method || "GET",
      signal: options.signal,
      headers: {
        ...(options.body === undefined
          ? {}
          : { "Content-Type": "application/json" }),
        ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {}),
      },
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw error;
    throw new ApiError(
      0,
      "Không kết nối được dịch vụ. Kiểm tra backend và thử lại.",
    );
  }
  const text = response.status === 204 ? "" : await response.text();
  let payload: unknown;
  try {
    payload = text ? parseJson(text) : undefined;
  } catch {
    payload = undefined;
  }
  if (!response.ok) {
    if (response.status === 401 && requestToken && requestToken === token)
      window.dispatchEvent(
        new CustomEvent("ktpm-unauthorized", { detail: requestToken }),
      );
    const message =
      (payload as { message?: string } | undefined)?.message ||
      `Dịch vụ trả lỗi HTTP ${response.status}.`;
    throw new ApiError(response.status, message);
  }
  if (text && payload === undefined)
    throw new ApiError(0, "Phản hồi dịch vụ không phải JSON hợp lệ.");
  return payload as T;
}
export function queryString(
  values: Record<string, string | number | undefined>,
) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.size ? "?" + params : "";
}
