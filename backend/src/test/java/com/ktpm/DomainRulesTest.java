package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;

import com.ktpm.auction.domain.*;
import com.ktpm.common.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  private Auction auction() {
    var a = new Auction();
    a.setSellerId(1L);
    a.setStartingPrice(new BigDecimal("0.10"));
    a.setCurrentPrice(new BigDecimal("0.10"));
    a.setMinimumBidStep(new BigDecimal("0.10"));
    a.setStatus("ACTIVE");
    a.setStartingTime(Instant.now().minusSeconds(60));
    a.setEndingTime(Instant.now().plusSeconds(60));
    return a;
  }

  @Test
  void firstBidMayEqualStartingPrice() {
    var a = auction();
    assertDoesNotThrow(() -> AuctionRules.canBid(a, 2L, new BigDecimal("0.10"), Instant.now()));
  }

  @Test
  void nextBidUsesExactDecimalStep() {
    var a = auction();
    a.setWinningBidId(9L);
    assertEquals(new BigDecimal("0.20"), AuctionRules.minimum(a));
    assertThrows(
        BusinessException.class,
        () -> AuctionRules.canBid(a, 2L, new BigDecimal("0.19"), Instant.now()));
  }

  @Test
  void ownerCannotBid() {
    var a = auction();
    assertThrows(
        BusinessException.class, () -> AuctionRules.canBid(a, 1L, BigDecimal.ONE, Instant.now()));
  }

  @Test
  void deadlineIsExclusive() {
    var a = auction();
    assertThrows(
        BusinessException.class,
        () -> AuctionRules.canBid(a, 2L, BigDecimal.ONE, a.getEndingTime()));
  }

  @Test
  void scheduledAndTerminalCannotBid() {
    var a = auction();
    for (String s : java.util.List.of("SCHEDULED", "ENDED", "FAILED")) {
      a.setStatus(s);
      assertThrows(
          BusinessException.class, () -> AuctionRules.canBid(a, 2L, BigDecimal.ONE, Instant.now()));
    }
  }

  @Test
  void invalidCreationIsRejected() {
    Instant now = Instant.now();
    assertThrows(
        BusinessException.class,
        () ->
            AuctionRules.validateCreate(BigDecimal.ZERO, BigDecimal.ONE, now, now.plusSeconds(60)));
    assertThrows(
        BusinessException.class,
        () -> AuctionRules.validateCreate(BigDecimal.ONE, BigDecimal.ONE, now, now));
  }
}
