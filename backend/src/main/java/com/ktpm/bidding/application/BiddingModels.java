package com.ktpm.bidding.application;

import com.ktpm.bidding.domain.Bid;
import java.math.BigDecimal;
import java.time.Instant;

public final class BiddingModels {
  private BiddingModels() {}

  public record BidResponse(
      Long id, Long auctionId, Long bidderId, BigDecimal amount, String status, Instant createdAt) {
    public static BidResponse from(Bid b) {
      return new BidResponse(
          b.getId(),
          b.getAuctionId(),
          b.getBidderId(),
          b.getAmount(),
          b.getStatus(),
          b.getCreatedAt());
    }
  }
}
