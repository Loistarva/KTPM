package com.ktpm.auction.infrastructure;

import com.ktpm.auction.domain.*;
import com.ktpm.common.*;
import com.ktpm.common.infrastructure.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAuctionStore implements AuctionStore {
  private final AuctionRepository repo;

  private Auction model(AuctionEntity e) {
    return JpaMapping.model(e, Auction::new);
  }

  public Optional<Auction> findById(Long id) {
    return repo.findById(id).map(this::model);
  }

  public Optional<Auction> lockById(Long id) {
    return repo.lockById(id).map(this::model);
  }

  public Auction saveAndFlush(Auction a) {
    return JpaMapping.save(a, repo, AuctionEntity::new);
  }

  public void delete(Long id) {
    repo.deleteById(id);
    repo.flush();
  }

  public boolean existsById(Long id) {
    return repo.existsById(id);
  }

  public Pages.Result<Auction> mine(Long seller, int page, int size) {
    return JpaPages.result(repo.findBySellerId(seller, JpaPages.request(page, size)), this::model);
  }

  public List<Long> findDue(Instant now) {
    return repo.findDue(now);
  }

  public Pages.Result<Auction> list(String status, String search, int page, int size) {
    return JpaPages.result(
        repo.findAll(
            (r, q, b) -> {
              List<Predicate> p = new ArrayList<>();
              if (status != null) p.add(b.equal(r.get("status"), status));
              if (search != null && !search.isBlank())
                p.add(
                    b.like(
                        b.lower(r.get("name")),
                        "%"
                            + search
                                .toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                            + "%",
                        '\\'));
              return b.and(p.toArray(Predicate[]::new));
            },
            JpaPages.request(page, size)),
        this::model);
  }
}
