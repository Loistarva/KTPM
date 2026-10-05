package com.ktpm.admin.application;

import com.ktpm.auction.domain.*;
import com.ktpm.bidding.domain.BidStore;
import com.ktpm.common.*;
import com.ktpm.user.application.UserDtos.Profile;
import com.ktpm.user.domain.UserStore;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminService {
  private final UserStore users;
  private final AuctionStore auctions;
  private final BidStore bids;
  private final MaintenanceGate maintenance;
  private final UnitOfWork work;

  public Pages.Result<Profile> users(int page, int size) {
    Pages.validate(page, size);
    return work.read(() -> Pages.map(users.list(page, size), Profile::from));
  }

  public void deleteAuction(Long id) {
    work.write(
        () -> {
          maintenance.exclusive();
          auctions.lockById(id).orElseThrow(() -> BusinessException.missing("Auction not found"));
          auctions.delete(id);
        });
  }

  public void deleteUser(Long admin, Long id) {
    if (admin.equals(id))
      throw BusinessException.forbidden("Cannot delete your own administrator account");
    work.write(
        () -> {
          maintenance.exclusive();
          users.lockById(id).orElseThrow(() -> BusinessException.missing("User not found"));
          var affected = bids.affected(id);
          users.delete(id);
          for (Long auctionId : affected)
            auctions
                .lockById(auctionId)
                .ifPresent(
                    a -> {
                      var remaining = bids.findByAuctionIdOrderByAmountDescIdDesc(auctionId);
                      boolean terminal = List.of("ENDED", "FAILED").contains(a.getStatus());
                      for (var b : remaining) b.setStatus(terminal ? "LOST" : "OUTBID");
                      bids.saveAllAndFlush(remaining);
                      if (remaining.isEmpty()) {
                        a.setWinningBidId(null);
                        a.setWinnerUserId(null);
                        a.setCurrentPrice(a.getStartingPrice());
                        a.setFinalPrice(null);
                        if (terminal) a.setStatus("FAILED");
                      } else {
                        var winner = remaining.get(0);
                        winner.setStatus(terminal ? "WON" : "WINNING");
                        bids.saveAndFlush(winner);
                        a.setWinningBidId(winner.getId());
                        a.setWinnerUserId(winner.getBidderId());
                        a.setCurrentPrice(winner.getAmount());
                        a.setFinalPrice(terminal ? winner.getAmount() : null);
                        if (terminal) a.setStatus("ENDED");
                      }
                      auctions.saveAndFlush(a);
                    });
        });
  }
}
