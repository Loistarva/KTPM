package com.ktpm.bidding.infrastructure;

import com.ktpm.bidding.domain.*;
import com.ktpm.common.Pages;
import com.ktpm.common.infrastructure.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBidStore implements BidStore {
  private final BidRepository repo;

  private Bid model(BidEntity e) {
    return JpaMapping.model(e, Bid::new);
  }

  public Optional<Bid> findById(Long id) {
    return repo.findById(id).map(this::model);
  }

  public Bid saveAndFlush(Bid b) {
    return JpaMapping.save(b, repo, BidEntity::new);
  }

  public void saveAllAndFlush(List<Bid> bids) {
    for (Bid b : bids) saveAndFlush(b);
  }

  public long count() {
    return repo.count();
  }

  public List<Bid> findByAuctionIdAndStatus(Long id, String status) {
    return repo.findByAuctionIdAndStatus(id, status).stream().map(this::model).toList();
  }

  public List<Bid> findByAuctionIdOrderByAmountDescIdDesc(Long id) {
    return repo.findByAuctionIdOrderByAmountDescIdDesc(id).stream().map(this::model).toList();
  }

  public List<Long> affected(Long user) {
    return repo.affected(user);
  }

  public Pages.Result<Bid> history(Long id, int page, int size) {
    return JpaPages.result(repo.findByAuctionId(id, JpaPages.request(page, size)), this::model);
  }

  public Pages.Result<Bid> mine(Long id, int page, int size) {
    return JpaPages.result(repo.findByBidderId(id, JpaPages.request(page, size)), this::model);
  }
}
