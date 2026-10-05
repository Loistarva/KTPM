package com.ktpm.bidding.application;

import com.ktpm.auction.application.AuctionService;
import com.ktpm.auction.domain.*;
import com.ktpm.bidding.application.BiddingModels.BidResponse;
import com.ktpm.bidding.domain.*;
import com.ktpm.common.*;
import com.ktpm.user.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BiddingService {
  private final AuctionService auctions;
  private final AuctionStore auctionStore;
  private final MaintenanceGate maintenance;
  private final UserService users;
  private final BidStore bids;
  private final UnitOfWork work;

  public BidResponse bid(Long id, Long user, BigDecimal amount) {
    return work.write(
        () -> {
          maintenance.shared();
          Auction a = auctions.lock(id);
          users.requireExists(user);
          AuctionRules.canBid(a, user, amount, Instant.now());
          if (a.getWinningBidId() != null) {
            Bid previous = bids.findById(a.getWinningBidId()).orElseThrow();
            previous.setStatus("OUTBID");
            bids.saveAndFlush(previous);
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
          auctions.get(id);
          return Pages.map(bids.history(id, page, size), BidResponse::from);
        });
  }

  public Pages.Result<BidResponse> mine(Long id, int page, int size) {
    Pages.validate(page, size);
    return work.read(() -> Pages.map(bids.mine(id, page, size), BidResponse::from));
  }

  public void close(Auction a, boolean success) {
    work.mandatory(
        () -> {
          for (Bid b : bids.findByAuctionIdAndStatus(a.getId(), "WINNING")) {
            b.setStatus(success ? "WON" : "LOST");
            bids.saveAndFlush(b);
          }
          for (Bid b : bids.findByAuctionIdAndStatus(a.getId(), "OUTBID")) {
            b.setStatus("LOST");
            bids.saveAndFlush(b);
          }
        });
  }
}
