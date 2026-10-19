package com.ktpm.common.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sample outside scrape requests so monitoring cannot flood the connection pool. */
@Component
public class PostgresMetrics {
  private final JdbcTemplate jdbc;
  private final MeterRegistry metrics;
  private final AtomicLong lockWaiters = new AtomicLong();
  private final AtomicLong due = new AtomicLong();
  private final AtomicLong lag = new AtomicLong();
  private final AtomicLong outbox = new AtomicLong();

  public PostgresMetrics(JdbcTemplate jdbc, MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.metrics = metrics;
    metrics.gauge("ktpm.db.lock.waiters", lockWaiters);
    metrics.gauge("ktpm.scheduler.due", due);
    metrics.gauge("ktpm.scheduler.lag.seconds", lag);
    metrics.gauge("ktpm.outbox.pending", outbox);
  }

  @Scheduled(fixedDelay = 15000, initialDelay = 15000)
  public void sample() {
    try {
      lockWaiters.set(
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",
              Long.class));
      jdbc.query(
          """
          SELECT count(*),COALESCE(EXTRACT(EPOCH FROM (clock_timestamp()-min(
            CASE WHEN status='SCHEDULED' THEN starting_time ELSE ending_time END))),0)::bigint
          FROM auctions WHERE (status='SCHEDULED' AND starting_time<=CURRENT_TIMESTAMP)
            OR (status='ACTIVE' AND ending_time<=CURRENT_TIMESTAMP)
          """,
          rs -> {
            if (rs.next()) {
              due.set(rs.getLong(1));
              lag.set(rs.getLong(2));
            }
            return null;
          });
      outbox.set(
          jdbc.queryForObject(
              "SELECT count(*) FROM auction_outbox WHERE published_at IS NULL", Long.class));
    } catch (Exception e) {
      metrics.counter("ktpm.metrics.sample-failures").increment();
    }
  }
}
