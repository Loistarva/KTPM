package com.ktpm.realtime.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL NOTIFY is delivered only after commit, atomically with the published marker. */
@Component
@ConditionalOnProperty(name = "ktpm.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final MeterRegistry metrics;
  private final int batch;
  private final int retentionHours;

  public OutboxRelay(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      MeterRegistry metrics,
      @Value("${ktpm.outbox.batch-size:100}") int batch,
      @Value("${ktpm.outbox.retention-hours:24}") int retentionHours) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(manager);
    this.transaction.setTimeout(5);
    this.metrics = metrics;
    this.batch = batch;
    this.retentionHours = retentionHours;
  }

  @Scheduled(fixedDelayString = "${ktpm.outbox.delay-ms:250}")
  public void publish() {
    try {
      transaction.executeWithoutResult(
          status -> {
            var rows =
                jdbc.query(
                    """
            SELECT id,auction_id FROM auction_outbox WHERE published_at IS NULL
            ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
            """,
                    (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)},
                    batch);
            Set<Long> auctions = new HashSet<>();
            for (var row : rows) auctions.add(row[1]);
            for (Long id : auctions)
              jdbc.queryForList("SELECT pg_notify('ktpm_auction_events', ?)", id.toString());
            if (!rows.isEmpty()) {
              Long[] ids = rows.stream().map(r -> r[0]).toArray(Long[]::new);
              jdbc.update(
                  connection -> {
                    var statement =
                        connection.prepareStatement(
                            "UPDATE auction_outbox SET published_at=clock_timestamp() WHERE id=ANY(?)");
                    statement.setArray(1, connection.createArrayOf("bigint", ids));
                    return statement;
                  });
            }
            metrics.counter("ktpm.outbox.published").increment(rows.size());
          });
    } catch (Exception e) {
      metrics.counter("ktpm.outbox.failures").increment();
      // No marker was committed: the next tick retries the same rows.
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox relay failed; retry next tick", e);
    }
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
  public void cleanup() {
    // Bound each cleanup so retention cannot create a large delete transaction.
    jdbc.update(
        """
        DELETE FROM auction_outbox WHERE id IN (
          SELECT id FROM auction_outbox WHERE published_at<clock_timestamp()-(? * interval '1 hour')
          ORDER BY published_at LIMIT 10000)
        """,
        retentionHours);
  }
}
