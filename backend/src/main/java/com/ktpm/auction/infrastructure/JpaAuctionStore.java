package com.ktpm.auction.infrastructure;

import com.ktpm.auction.domain.*;
import com.ktpm.common.*;
import com.ktpm.common.infrastructure.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAuctionStore implements AuctionStore {
  private final AuctionRepository repo;
  private final JdbcTemplate jdbc;
  private final MeterRegistry metrics;

  private Auction model(AuctionEntity e) {
    return JpaMapping.model(e, Auction::new);
  }

  public Optional<Auction> findById(Long id) {
    return repo.findById(id).map(this::model);
  }

  public Optional<Auction> lockById(Long id) {
    return repo.lockById(id).map(this::model);
  }

  public boolean reserveBid(Auction observed, Long bidder, BigDecimal amount) {
    Timer.Sample sample = Timer.start(metrics);
    try {
      var reserved =
          jdbc.query(
              """
          UPDATE auctions SET current_price=?, winner_user_id=?, updated_at=clock_timestamp()
          WHERE id=? AND current_price=? AND winning_bid_id IS NOT DISTINCT FROM CAST(? AS bigint)
            AND status='ACTIVE' AND starting_time<=clock_timestamp() AND ending_time>clock_timestamp()
          RETURNING ending_time>clock_timestamp()
          """,
              (rs, row) -> rs.getBoolean(1),
              amount,
              bidder,
              observed.getId(),
              observed.getCurrentPrice(),
              observed.getWinningBidId());
      if (!reserved.isEmpty() && !reserved.get(0))
        throw BusinessException.conflict("Auction is not active");
      return !reserved.isEmpty();
    } finally {
      sample.stop(metrics.timer("ktpm.bid.reserve", "operation", "compare-and-set"));
    }
  }

  public boolean ownsClaim(Long id, UUID token) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT lifecycle_claim_token=? AND lifecycle_claim_until>clock_timestamp()
        FROM auctions WHERE id=?
        """,
            Boolean.class,
            token,
            id));
  }

  public void completeClaim(Long id, UUID token) {
    jdbc.update(
        """
        UPDATE auctions SET lifecycle_claim_token=NULL, lifecycle_claim_until=NULL,
          lifecycle_retry_at=NULL, lifecycle_attempts=0
        WHERE id=? AND lifecycle_claim_token=?
        """,
        id,
        token);
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
