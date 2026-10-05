package com.ktpm.auction.application;

import com.ktpm.auction.domain.*;
import com.ktpm.bidding.application.BiddingService;
import com.ktpm.common.UnitOfWork;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionLifecycleService {
  private final AuctionService auctions;
  private final BiddingService bidding;
  private final MaintenanceGate maintenance;
  private final AuctionStore store;
  private final UnitOfWork work;

  public void advance(Long id, Instant now) {
    work.write(
        () -> {
          maintenance.shared();
          Auction a = auctions.lock(id);
          if ("SCHEDULED".equals(a.getStatus()) && !a.getStartingTime().isAfter(now))
            a.setStatus("ACTIVE");
          if ("ACTIVE".equals(a.getStatus()) && !a.getEndingTime().isAfter(now)) {
            boolean success = a.getWinningBidId() != null;
            a.setFinalPrice(success ? a.getCurrentPrice() : null);
            if (!success) a.setWinnerUserId(null);
            bidding.close(a, success);
            a.setStatus(success ? "ENDED" : "FAILED");
          }
          store.saveAndFlush(a);
        });
  }
}
