package com.ktpm.auction.api;

import com.ktpm.auction.application.AuctionModels;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class AuctionDtos {
  private AuctionDtos() {}

  public record CreateAuctionRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 5000) String description,
      @NotBlank @Size(max = 50) String condition,
      @Size(max = 2000)
          @Pattern(
              regexp = "(?i)https?://[^\\s]+",
              message = "Image URL must start with http:// or https:// and contain no spaces")
          String imageUrl,
      @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal startingPrice,
      @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal minimumBidStep,
      @NotNull Instant startingTime,
      @NotNull Instant endingTime) {
    public AuctionModels.CreateAuctionRequest command() {
      return new AuctionModels.CreateAuctionRequest(
          name,
          description,
          condition,
          imageUrl,
          startingPrice,
          minimumBidStep,
          startingTime,
          endingTime);
    }
  }
}
