import Decimal from "decimal.js";
import type { Auction, ID, Money } from "../types";
Decimal.set({ precision: 40 });
export const dec = (value: Money) => new Decimal(String(value));
export const sameId = (a: ID | null | undefined, b: ID | null | undefined) =>
  a != null && b != null && String(a) === String(b);
export function validateMoney(value: string): string | null {
  if (!/^\d{1,17}(\.\d{1,2})?$/.test(value.trim()))
    return "Nhập số dương, tối đa 17 chữ số nguyên và 2 chữ số thập phân, dùng dấu chấm.";
  if (!dec(value.trim()).gt(0)) return "Số tiền phải lớn hơn 0.";
  return null;
}
export function money(value: Money | null | undefined): string {
  if (value == null) return "—";
  const parts = dec(value)
    .toFixed(dec(value).decimalPlaces() ? 2 : 0)
    .split(".");
  return (
    parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".") +
    (parts[1] ? "," + parts[1] : "") +
    " ₫"
  );
}
export const minimumBid = (a: Auction) =>
  a.winningBidId == null
    ? dec(a.startingPrice)
    : dec(a.currentPrice).plus(a.minimumBidStep);
export function date(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Ho_Chi_Minh",
      }).format(new Date(value))
    : "—";
}
export const localToUtc = (value: string) => new Date(value).toISOString();
export function localDateInput(dateValue: Date) {
  const d = new Date(
    dateValue.getTime() - dateValue.getTimezoneOffset() * 60000,
  );
  return d.toISOString().slice(0, 16);
}
