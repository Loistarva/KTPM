package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;

import com.ktpm.auction.infrastructure.AuctionJobQueue;
import com.ktpm.common.BusinessException;
import com.ktpm.common.UnitOfWork;
import com.ktpm.realtime.infrastructure.OutboxRelay;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class PhaseTwoIntegrationTest extends TestSupport {
  @Autowired AuctionJobQueue jobs;
  @Autowired UnitOfWork work;
  @Autowired DataSource source;
  @Autowired PlatformTransactionManager manager;
  @Autowired MeterRegistry metrics;

  private void expire(Long id) {
    jdbc.update(
        "UPDATE auctions SET ending_time=clock_timestamp()-interval '1 millisecond' WHERE id=?",
        id);
  }

  @Test
  void staleSnapshotCannotReplaceNewLeader() {
    var a = auction(account().id());
    var old = auctionRepo.findById(a.id()).orElseThrow();
    var first = account();
    var second = account();
    bid(a.id(), first.id(), "100");
    assertFalse(work.write(() -> auctionRepo.reserveBid(old, second.id(), new BigDecimal("110"))));
    assertEquals(first.id(), auctions.get(a.id()).winnerUserId());
    assertEquals(1, bids.count());
  }

  @Test
  void bidWaitingOnRowLockCannotCrossDeadline() throws Exception {
    var a = auction(account().id());
    var user = account();
    jdbc.update(
        "UPDATE auctions SET ending_time=clock_timestamp()+interval '1 second' WHERE id=?", a.id());
    var executor = Executors.newSingleThreadExecutor();
    try (var blocker = source.getConnection()) {
      blocker.setAutoCommit(false);
      try (var statement =
          blocker.prepareStatement("SELECT id FROM auctions WHERE id=? FOR UPDATE")) {
        statement.setLong(1, a.id());
        statement.execute();
      }
      var attempted =
          executor.submit(
              () -> assertThrows(BusinessException.class, () -> bid(a.id(), user.id(), "100")));
      long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",
              Integer.class)
          == 0) {
        assertTrue(System.nanoTime() < timeout, "Bid should reach the reservation lock");
        Thread.sleep(10);
      }
      try (var s = blocker.createStatement()) {
        s.execute("SELECT pg_sleep(1.1)");
      }
      blocker.commit();
      assertEquals(409, attempted.get(5, TimeUnit.SECONDS).status.value());
    } finally {
      executor.shutdownNow();
    }
    assertEquals(0, bids.count());
    assertNull(auctions.get(a.id()).winnerUserId());
  }

  @Test
  void separateWorkersClaimDisjointBatches() throws Exception {
    var seller = account();
    for (int i = 0; i < 20; i++) expire(auction(seller.id()).id());
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    Callable<List<AuctionJobQueue.Claim>> action =
        () -> {
          start.await();
          return jobs.claim(10, Duration.ofSeconds(30));
        };
    try {
      var first = executor.submit(action);
      var second = executor.submit(action);
      start.countDown();
      Set<Long> claimed = new HashSet<>();
      for (var task : List.of(first, second)) {
        var batch = task.get(5, TimeUnit.SECONDS);
        assertEquals(10, batch.size());
        for (var job : batch) assertTrue(claimed.add(job.auctionId()), "Claimed twice");
      }
      assertEquals(20, claimed.size());
      assertTrue(jobs.claim(10, Duration.ofSeconds(30)).isEmpty());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void lockedAuctionIsSkippedWithoutHoldingUpOtherJobs() throws Exception {
    var seller = account();
    var first = auction(seller.id());
    var second = auction(seller.id());
    expire(first.id());
    expire(second.id());
    try (var blocker = source.getConnection()) {
      blocker.setAutoCommit(false);
      try (var s = blocker.prepareStatement("SELECT id FROM auctions WHERE id=? FOR UPDATE")) {
        s.setLong(1, first.id());
        s.execute();
      }
      var batch = jobs.claim(10, Duration.ofSeconds(30));
      assertEquals(
          List.of(second.id()), batch.stream().map(AuctionJobQueue.Claim::auctionId).toList());
      blocker.rollback();
    }
  }

  @Test
  void expiredLeaseIsReclaimedAndOldWorkerIsFenced() {
    var a = auction(account().id());
    expire(a.id());
    var old = jobs.claim(1, Duration.ofSeconds(30)).get(0);
    jdbc.update(
        "UPDATE auctions SET lifecycle_claim_until=clock_timestamp()-interval '1 second' WHERE id=?",
        a.id());
    var current = jobs.claim(1, Duration.ofSeconds(30)).get(0);
    assertNotEquals(old.token(), current.token());
    lifecycle.advanceClaimed(a.id(), old.token());
    assertEquals("ACTIVE", auctions.get(a.id()).status());
    assertEquals(
        current.token(),
        jdbc.queryForObject(
            "SELECT lifecycle_claim_token FROM auctions WHERE id=?", UUID.class, a.id()));
    lifecycle.advanceClaimed(a.id(), current.token());
    assertEquals("FAILED", auctions.get(a.id()).status());
    assertNull(
        jdbc.queryForObject(
            "SELECT lifecycle_claim_token FROM auctions WHERE id=?", UUID.class, a.id()));
    lifecycle.advanceClaimed(a.id(), old.token());
    assertEquals("FAILED", auctions.get(a.id()).status());
  }

  @Test
  void failedJobBacksOffAndCanBeRetried() {
    var a = auction(account().id());
    expire(a.id());
    var claim = jobs.claim(1, Duration.ofSeconds(30)).get(0);
    jobs.retry(claim);
    assertTrue(jobs.claim(1, Duration.ofSeconds(30)).isEmpty());
    jdbc.update(
        "UPDATE auctions SET lifecycle_retry_at=clock_timestamp()-interval '1 second' WHERE id=?",
        a.id());
    var retry = jobs.claim(1, Duration.ofSeconds(30)).get(0);
    lifecycle.advanceClaimed(retry.auctionId(), retry.token());
    assertEquals("FAILED", auctions.get(a.id()).status());
  }

  @Test
  void rollbackLeavesNoBidOrOutboxNotification() {
    var a = auction(account().id());
    var user = account();
    var before = jdbc.queryForObject("SELECT count(*) FROM auction_outbox", Long.class);
    assertThrows(
        IllegalStateException.class,
        () ->
            work.write(
                () -> {
                  bid(a.id(), user.id(), "100");
                  throw new IllegalStateException("rollback");
                }));
    assertEquals(0, bids.count());
    assertNull(auctions.get(a.id()).winningBidId());
    assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM auction_outbox", Long.class));
  }

  @Test
  void relayBroadcastsToEveryInstanceAndDoesNotRepublishAcknowledgedRows() throws Exception {
    var a = auction(account().id());
    try (var first = source.getConnection();
        var second = source.getConnection()) {
      for (var c : List.of(first, second))
        try (var s = c.createStatement()) {
          s.execute("LISTEN ktpm_auction_events");
        }
      var relay = new OutboxRelay(jdbc, manager, metrics, 100, 24);
      relay.publish();
      for (var c : List.of(first, second)) {
        var notifications = c.unwrap(PGConnection.class).getNotifications(3000);
        assertNotNull(notifications);
        assertEquals(Long.toString(a.id()), notifications[0].getParameter());
      }
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM auction_outbox WHERE published_at IS NULL", Integer.class));
      relay.publish();
      var repeated = first.unwrap(PGConnection.class).getNotifications(50);
      assertTrue(repeated == null || repeated.length == 0);
      for (var c : List.of(first, second))
        try (var s = c.createStatement()) {
          s.execute("UNLISTEN *");
        }
    }
  }

  @Test
  void relayFailureRollsBackAcknowledgementAndRetries() {
    auction(account().id());
    var fail = new AtomicBoolean(true);
    var unstable =
        new JdbcTemplate(source) {
          @Override
          public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
            if (sql.contains("pg_notify") && fail.getAndSet(false))
              throw new IllegalStateException("simulated outage");
            return super.queryForList(sql, arguments);
          }
        };
    var relay = new OutboxRelay(unstable, manager, metrics, 100, 24);
    relay.publish();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM auction_outbox WHERE published_at IS NULL", Integer.class));
    relay.publish();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM auction_outbox WHERE published_at IS NULL", Integer.class));
  }
}
