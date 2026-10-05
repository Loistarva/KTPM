package com.ktpm.auction.domain;

import com.ktpm.common.Pages;
import java.time.Instant;
import java.util.*;

public interface AuctionStore {
  Optional<Auction> findById(Long id);

  Optional<Auction> lockById(Long id);

  Auction saveAndFlush(Auction a);

  void delete(Long id);

  boolean existsById(Long id);

  Pages.Result<Auction> mine(Long seller, int page, int size);

  Pages.Result<Auction> list(String status, String search, int page, int size);

  List<Long> findDue(Instant now);
}
