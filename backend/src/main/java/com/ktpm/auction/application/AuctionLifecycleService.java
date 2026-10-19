package com.ktpm.auction.application;

import com.ktpm.auction.domain.*;
import com.ktpm.bidding.application.BiddingService;
import com.ktpm.common.UnitOfWork;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionLifecycleService {
  private final BiddingService bidding;
  private final MaintenanceGate maintenance;
  private final AuctionStore store;
  private final UnitOfWork work;

  public void advance(Long id, Instant now) {
    advance(id, now, null);
  }

  public void advanceClaimed(Long id, UUID token) {
    advance(id, null, token);
  }

  private void advance(Long id, Instant suppliedTime, UUID token) {
    work.write(
        () -> {
          maintenance.shared();
          Auction a = store.lockById(id).orElse(null);
          if (a == null) return;
          if (token != null && !store.ownsClaim(id, token)) return;
          Instant now = suppliedTime == null ? Instant.now() : suppliedTime;
          String previous = a.getStatus();
          if ("SCHEDULED".equals(a.getStatus()) && !a.getStartingTime().isAfter(now))
            a.setStatus("ACTIVE");
          if ("ACTIVE".equals(a.getStatus()) && !a.getEndingTime().isAfter(now)) {
            boolean success = a.getWinningBidId() != null;
            a.setFinalPrice(success ? a.getCurrentPrice() : null);
            if (!success) a.setWinnerUserId(null);
            bidding.close(a, success);
            a.setStatus(success ? "ENDED" : "FAILED");
          }
          if (!previous.equals(a.getStatus())) store.saveAndFlush(a);
          if (token != null) store.completeClaim(id, token);
        });
  }
}
