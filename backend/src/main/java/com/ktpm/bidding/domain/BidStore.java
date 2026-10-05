package com.ktpm.bidding.domain;

import com.ktpm.common.Pages;
import java.util.*;

public interface BidStore {
  Optional<Bid> findById(Long id);

  Bid saveAndFlush(Bid bid);

  void saveAllAndFlush(List<Bid> bids);

  long count();

  List<Bid> findByAuctionIdAndStatus(Long id, String status);

  List<Bid> findByAuctionIdOrderByAmountDescIdDesc(Long id);

  List<Long> affected(Long user);

  Pages.Result<Bid> history(Long auction, int page, int size);

  Pages.Result<Bid> mine(Long user, int page, int size);
}
