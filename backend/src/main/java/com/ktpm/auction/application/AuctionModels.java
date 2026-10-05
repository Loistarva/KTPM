package com.ktpm.auction.application;

import com.ktpm.auction.domain.Auction;
import java.math.BigDecimal;
import java.time.Instant;

public final class AuctionModels {
  private AuctionModels() {}

  public record CreateAuctionRequest(
      String name,
      String description,
      String condition,
      String imageUrl,
      BigDecimal startingPrice,
      BigDecimal minimumBidStep,
      Instant startingTime,
      Instant endingTime) {}

  public record AuctionResponse(
      Long id,
      Long sellerId,
      String name,
      String description,
      String condition,
      String imageUrl,
      BigDecimal startingPrice,
      BigDecimal currentPrice,
      BigDecimal finalPrice,
      BigDecimal minimumBidStep,
      Instant startingTime,
      Instant endingTime,
      String status,
      Long winnerUserId,
      Long winningBidId,
      Instant createdAt) {
    public static AuctionResponse from(Auction a) {
      return new AuctionResponse(
          a.getId(),
          a.getSellerId(),
          a.getName(),
          a.getDescription(),
          a.getCondition(),
          a.getImageUrl(),
          a.getStartingPrice(),
          a.getCurrentPrice(),
          a.getFinalPrice(),
          a.getMinimumBidStep(),
          a.getStartingTime(),
          a.getEndingTime(),
          a.getStatus(),
          a.getWinnerUserId(),
          a.getWinningBidId(),
          a.getCreatedAt());
    }
  }
}
