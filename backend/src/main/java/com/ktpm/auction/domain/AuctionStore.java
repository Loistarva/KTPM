package com.ktpm.auction.domain;

import com.ktpm.common.Pages;
import java.math.BigDecimal;
import java.util.*;

public interface AuctionStore {
  Optional<Auction> findById(Long id);

  Optional<Auction> lockById(Long id);

  /**
   * Reserve a new price only if the observed leader is still current and the deadline has not
   * passed.
   */
  boolean reserveBid(Auction observed, Long bidder, BigDecimal amount);

  boolean ownsClaim(Long id, UUID token);

  void completeClaim(Long id, UUID token);

  Auction saveAndFlush(Auction a);

  void delete(Long id);

  boolean existsById(Long id);

  Pages.Result<Auction> mine(Long seller, int page, int size);

  Pages.Result<Auction> list(String status, String search, int page, int size);
}
