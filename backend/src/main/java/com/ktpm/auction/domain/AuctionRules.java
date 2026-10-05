package com.ktpm.auction.domain;

import com.ktpm.common.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;

public final class AuctionRules {
  private AuctionRules() {}

  public static void validateCreate(
      BigDecimal starting, BigDecimal step, Instant start, Instant end) {
    if (starting.signum() <= 0 || step.signum() <= 0)
      throw BusinessException.bad("Prices and step must be positive");
    if (!start.isBefore(end)) throw BusinessException.bad("startingTime must be before endingTime");
    if (!end.isAfter(Instant.now()))
      throw BusinessException.bad("endingTime must be in the future");
  }

  public static BigDecimal minimum(Auction a) {
    return a.getWinningBidId() == null
        ? a.getStartingPrice()
        : a.getCurrentPrice().add(a.getMinimumBidStep());
  }

  public static void canBid(Auction a, Long userId, BigDecimal amount, Instant now) {
    if (!"ACTIVE".equals(a.getStatus())
        || now.isBefore(a.getStartingTime())
        || !now.isBefore(a.getEndingTime()))
      throw BusinessException.conflict("Auction is not active");
    if (a.getSellerId().equals(userId))
      throw BusinessException.forbidden("Cannot bid on your own auction");
    if (amount == null || amount.compareTo(minimum(a)) < 0)
      throw BusinessException.conflict("Minimum allowed bid is " + minimum(a));
  }
}
