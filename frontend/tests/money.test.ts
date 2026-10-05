import { describe, expect, it } from "vitest";
import {
  date,
  dec,
  localToUtc,
  minimumBid,
  money,
  sameId,
  validateMoney,
} from "../src/lib/money";
import { parseJson } from "../src/lib/api";
import type { Auction } from "../src/types";
const auction = {
  startingPrice: "100000",
  currentPrice: "100000",
  minimumBidStep: "10000",
  winningBidId: null,
  winnerUserId: null,
} as Auction;
describe("Exact money and auction constraints", () => {
  it("accepts first bid equal to starting price", () =>
    expect(minimumBid(auction).toString()).toBe("100000"));
  it("requires a step even when current price equals starting price", () =>
    expect(minimumBid({ ...auction, winningBidId: 1 }).toString()).toBe(
      "110000",
    ));
  it.each(["0", "-1", "1,00", "1e3", "0.001", "100000000000000000", "NaN", ""])(
    "rejects invalid money %s",
    (value) => expect(validateMoney(value)).not.toBeNull(),
  );
  it("accepts the full NUMERIC(19,2) input range", () =>
    expect(validateMoney("99999999999999999.99")).toBeNull());
  it("preserves decimals and large amounts in JSON and calculations", () => {
    const payload = parseJson(
      '{"amount":99999999999999999.99,"id":9223372036854775806,"page":0}',
    ) as Record<string, string | number>;
    expect(payload.amount).toBe("99999999999999999.99");
    expect(payload.id).toBe("9223372036854775806");
    expect(payload.page).toBe(0);
    expect(dec(payload.amount).minus(".01").toFixed(2)).toBe(
      "99999999999999999.98",
    );
  });
  it("shows fractional money instead of silently rounding VND", () =>
    expect(money("1234567.50")).toBe("1.234.567,50 ₫"));
  it("handles integer and null money", () => {
    expect(money("100000")).toBe("100.000 ₫");
    expect(money(null)).toBe("—");
  });
  it("compares IDs without rounding them", () => {
    expect(sameId(1, "1")).toBe(true);
    expect(sameId(null, null)).toBe(false);
    expect(sameId("9223372036854775806", "9223372036854775807")).toBe(false);
  });
  it("converts a valid local date to ISO and renders Vietnam timezone", () => {
    expect(localToUtc("2026-10-05T12:00")).toMatch(/^2026-10-05T.*Z$/);
    expect(date("2026-10-05T00:00:00Z")).toContain("07:00");
  });
});
