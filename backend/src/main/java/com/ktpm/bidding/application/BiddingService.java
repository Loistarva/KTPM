package com.ktpm.bidding.application;

import com.ktpm.auction.domain.*;
import com.ktpm.bidding.application.BiddingModels.BidResponse;
import com.ktpm.bidding.domain.*;
import com.ktpm.common.*;
import com.ktpm.common.ContentionObserver;
import com.ktpm.user.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BiddingService {
  private final AuctionStore auctionStore;
  private final MaintenanceGate maintenance;
  private final UserService users;
  private final BidStore bids;
  private final UnitOfWork work;
  private final ContentionObserver contention;

  public BidResponse bid(Long id, Long user, BigDecimal amount) {
    // Every retry opens a fresh transaction and rechecks the current price and deadline.
    // Only a failed reservation is retried; an accepted bid is never replayed.
    for (int attempt = 0; attempt < 16; attempt++) {
      try {
        return place(id, user, amount);
      } catch (ReservationChanged e) {
        contention.bidRetry();
      }
    }
    throw BusinessException.conflict("Auction changed repeatedly; refresh and submit again");
  }

  private BidResponse place(Long id, Long user, BigDecimal amount) {
    return work.write(
        () -> {
          maintenance.shared();
          users.requireExists(user);
          Auction a =
              auctionStore
                  .findById(id)
                  .orElseThrow(() -> BusinessException.missing("Auction not found"));
          AuctionRules.canBid(a, user, amount, Instant.now());
          if (!auctionStore.reserveBid(a, user, amount)) throw new ReservationChanged();
          if (a.getWinningBidId() != null) {
            bids.outbid(a.getWinningBidId());
          }
          Bid b = new Bid();
          b.setAuctionId(id);
          b.setBidderId(user);
          b.setAmount(amount);
          bids.saveAndFlush(b);
          a.setCurrentPrice(amount);
          a.setWinnerUserId(user);
          a.setWinningBidId(b.getId());
          auctionStore.saveAndFlush(a);
          return BidResponse.from(b);
        });
  }

  public Pages.Result<BidResponse> history(Long id, int page, int size) {
    Pages.validate(page, size);
    return work.read(
        () -> {
          if (!auctionStore.existsById(id)) throw BusinessException.missing("Auction not found");
          return Pages.map(bids.history(id, page, size), BidResponse::from);
        });
  }

  public Pages.Result<BidResponse> mine(Long id, int page, int size) {
    Pages.validate(page, size);
    return work.read(() -> Pages.map(bids.mine(id, page, size), BidResponse::from));
  }

  public void close(Auction a, boolean success) {
    work.mandatory(() -> bids.closeAuction(a.getId(), success));
  }

  private static final class ReservationChanged extends RuntimeException {
    private ReservationChanged() {
      super(null, null, false, false);
    }
  }
}
