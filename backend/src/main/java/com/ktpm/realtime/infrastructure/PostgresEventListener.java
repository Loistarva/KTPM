package com.ktpm.realtime.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.sql.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Each API instance listens, so a bid handled by any instance reaches every connected viewer. */
@Component
@ConditionalOnProperty(name = "ktpm.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class PostgresEventListener {
  private static final Logger log = LoggerFactory.getLogger(PostgresEventListener.class);
  private final DataSource source;
  private final AuctionEventHub hub;
  private final MeterRegistry metrics;
  private final ExecutorService listener =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "postgres-events"));
  private volatile boolean running = true;
  private volatile Connection connection;

  public PostgresEventListener(DataSource source, AuctionEventHub hub, MeterRegistry metrics) {
    this.source = source;
    this.hub = hub;
    this.metrics = metrics;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    listener.execute(this::listen);
  }

  private void listen() {
    while (running) {
      try (Connection c = source.getConnection()) {
        connection = c;
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
          s.execute("LISTEN ktpm_auction_events");
        }
        hub.resync();
        var pg = c.unwrap(PGConnection.class);
        while (running) {
          var notifications = pg.getNotifications(1000);
          if (notifications != null)
            for (var notification : notifications) hub.changed(notification.getParameter());
        }
      } catch (Exception e) {
        if (!running) break;
        metrics.counter("ktpm.sse.listener-reconnects").increment();
        log.warn("Realtime database connection lost; reconnecting", e);
        // Existing streams also reconnect; the client falls back to periodic refresh meanwhile.
        hub.degraded();
        try {
          TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        }
      } finally {
        connection = null;
      }
    }
  }

  @PreDestroy
  public void stop() throws SQLException {
    running = false;
    if (connection != null) connection.close();
    listener.shutdownNow();
  }
}
