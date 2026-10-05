package com.ktpm.bidding.api;

import com.ktpm.bidding.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class BiddingDtos {
  private BiddingDtos() {}

  public record PlaceBid(
      @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {}
}
