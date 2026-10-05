package com.ktpm.auction.application;

import com.ktpm.auction.application.AuctionModels.*;
import com.ktpm.auction.domain.*;
import com.ktpm.common.*;
import com.ktpm.user.application.UserService;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionService {
  private final AuctionStore auctions;
  private final UserService users;
  private final MaintenanceGate maintenance;
  private final UnitOfWork work;

  public AuctionResponse create(Long seller, CreateAuctionRequest r) {
    return work.write(
        () -> {
          maintenance.shared();
          users.requireExists(seller);
          AuctionRules.validateCreate(
              r.startingPrice(), r.minimumBidStep(), r.startingTime(), r.endingTime());
          Auction a = new Auction();
          a.setSellerId(seller);
          a.setName(r.name().trim());
          a.setDescription(r.description().trim());
          a.setCondition(r.condition().trim());
          a.setImageUrl(r.imageUrl());
          a.setStartingPrice(r.startingPrice());
          a.setCurrentPrice(r.startingPrice());
          a.setMinimumBidStep(r.minimumBidStep());
          a.setStartingTime(r.startingTime());
          a.setEndingTime(r.endingTime());
          if (!r.startingTime().isAfter(Instant.now())) a.setStatus("ACTIVE");
          return AuctionResponse.from(auctions.saveAndFlush(a));
        });
  }

  public Auction lock(Long id) {
    return auctions.lockById(id).orElseThrow(() -> BusinessException.missing("Auction not found"));
  }

  public AuctionResponse get(Long id) {
    return work.read(
        () ->
            AuctionResponse.from(
                auctions
                    .findById(id)
                    .orElseThrow(() -> BusinessException.missing("Auction not found"))));
  }

  public Pages.Result<AuctionResponse> mine(Long seller, int page, int size) {
    Pages.validate(page, size);
    return work.read(() -> Pages.map(auctions.mine(seller, page, size), AuctionResponse::from));
  }

  public Pages.Result<AuctionResponse> list(String status, String search, int page, int size) {
    Pages.validate(page, size);
    if (status != null && !List.of("SCHEDULED", "ACTIVE", "ENDED", "FAILED").contains(status))
      throw BusinessException.bad("Invalid auction status");
    if (search != null && search.length() > 200) throw BusinessException.bad("Search too long");
    return work.read(
        () -> Pages.map(auctions.list(status, search, page, size), AuctionResponse::from));
  }

  public void delete(Long seller, Long id) {
    work.write(
        () -> {
          maintenance.shared();
          Auction a = lock(id);
          if (!seller.equals(a.getSellerId()))
            throw BusinessException.forbidden("Auction belongs to another seller");
          if (a.getWinningBidId() != null)
            throw BusinessException.conflict("Only auctions without bids can be deleted by seller");
          auctions.delete(id);
        });
  }

  public List<Long> due(Instant now) {
    return work.read(() -> auctions.findDue(now));
  }
}
