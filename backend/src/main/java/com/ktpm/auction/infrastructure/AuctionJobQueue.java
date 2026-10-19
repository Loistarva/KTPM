package com.ktpm.auction.infrastructure;

import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Leases survive worker crashes; tokens fence delayed workers after a lease is reassigned. */
@Component
@RequiredArgsConstructor
public class AuctionJobQueue {
  private final JdbcTemplate jdbc;

  public record Claim(Long auctionId, UUID token) {}

  @Transactional
  public List<Claim> claim(int limit, Duration lease) {
    UUID token = UUID.randomUUID();
    return jdbc.query(
        """
        WITH due AS MATERIALIZED (
          SELECT id FROM auctions
          WHERE ((status='SCHEDULED' AND starting_time<=CURRENT_TIMESTAMP)
              OR (status='ACTIVE' AND ending_time<=CURRENT_TIMESTAMP))
            AND (lifecycle_claim_until IS NULL OR lifecycle_claim_until<=clock_timestamp())
            AND (lifecycle_retry_at IS NULL OR lifecycle_retry_at<=clock_timestamp())
          ORDER BY CASE WHEN status='SCHEDULED' THEN starting_time ELSE ending_time END,id
          LIMIT ? FOR UPDATE SKIP LOCKED
        )
        UPDATE auctions a SET lifecycle_claim_token=?,
          lifecycle_claim_until=clock_timestamp()+(? * interval '1 millisecond'),
          lifecycle_attempts=lifecycle_attempts+1
        FROM due WHERE a.id=due.id RETURNING a.id
        """,
        (rs, row) -> new Claim(rs.getLong(1), token),
        limit,
        token,
        lease.toMillis());
  }

  @Transactional
  public void retry(Claim claim) {
    jdbc.update(
        """
        UPDATE auctions SET lifecycle_claim_token=NULL,lifecycle_claim_until=NULL,
          lifecycle_retry_at=clock_timestamp()+
            (LEAST(60, power(2, LEAST(lifecycle_attempts,6))) * interval '1 second')
        WHERE id=? AND lifecycle_claim_token=?
        """,
        claim.auctionId(),
        claim.token());
  }
}
