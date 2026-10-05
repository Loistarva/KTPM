package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;

import com.ktpm.auction.application.AuctionModels.CreateAuctionRequest;
import com.ktpm.common.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class ConcurrencyIntegrationTest extends TestSupport {
  private void race(int n, IntConsumer action) throws Exception {
    var pool = Executors.newFixedThreadPool(n);
    var start = new CountDownLatch(1);
    List<Future<?>> tasks = new ArrayList<>();
    try {
      for (int i = 0; i < n; i++) {
        final int id = i;
        tasks.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                    action.accept(id);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      }
      start.countDown();
      for (var t : tasks) t.get(20, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void twelveBiddersLeaveHighestAcceptedBidAndSingleLeader() throws Exception {
    var a = auction(account().id());
    var bidderIds = new ArrayList<Long>();
    for (int i = 0; i < 12; i++) bidderIds.add(account().id());
    race(
        12,
        i -> {
          try {
            bid(a.id(), bidderIds.get(i), Integer.toString(100 + i * 10));
          } catch (BusinessException e) {
            assertEquals(409, e.status.value());
          }
        });
    var r = auctions.get(a.id());
    assertEquals(0, r.currentPrice().compareTo(new BigDecimal("210")));
    assertEquals(1, bids.findByAuctionIdAndStatus(a.id(), "WINNING").size());
    assertEquals(bidderIds.get(11), r.winnerUserId());
  }

  @Test
  void equalConcurrentBidsOnlyAcceptOne() throws Exception {
    var a = auction(account().id());
    var ids = List.of(account().id(), account().id());
    var count = new java.util.concurrent.atomic.AtomicInteger();
    race(
        2,
        i -> {
          try {
            bid(a.id(), ids.get(i), "100");
            count.incrementAndGet();
          } catch (BusinessException e) {
            assertEquals(409, e.status.value());
          }
        });
    assertEquals(1, count.get());
    assertEquals(1, bids.count());
  }

  @Test
  void repeatedFinalizationHasOneStableWinner() throws Exception {
    var a = auction(account().id());
    var bidder = account();
    bid(a.id(), bidder.id(), "100");
    race(8, i -> lifecycle.advance(a.id(), a.endingTime().plusSeconds(1)));
    var r = auctions.get(a.id());
    assertEquals("ENDED", r.status());
    assertEquals(bidder.id(), r.winnerUserId());
    assertEquals(1, bids.findByAuctionIdAndStatus(a.id(), "WON").size());
    assertEquals(0, bids.findByAuctionIdAndStatus(a.id(), "WINNING").size());
  }

  @Test
  void scheduledRecoveryAndNoBidEnd() {
    var now = Instant.now();
    var a =
        auctions.create(
            account().id(),
            new CreateAuctionRequest(
                "Book",
                "Book description",
                "USED",
                null,
                BigDecimal.TEN,
                BigDecimal.ONE,
                now.plusSeconds(60),
                now.plusSeconds(120)));
    lifecycle.advance(a.id(), now.plusSeconds(180));
    var r = auctions.get(a.id());
    assertEquals("FAILED", r.status());
    assertNull(r.winnerUserId());
    assertNull(r.finalPrice());
  }

  @Test
  void expiredBidIsRejectedEvenBeforeSchedulerRuns() {
    var a = auction(account().id());
    jdbc.update(
        "UPDATE auctions SET ending_time=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusMillis(1)),
        a.id());
    assertThrows(BusinessException.class, () -> bid(a.id(), account().id(), "100"));
    assertEquals(0, bids.count());
  }

  @Test
  void cleanupRacingBidLeavesNoOrphans() throws Exception {
    var administrator = administrator();
    var a = auction(account().id());
    var bidder = account();
    race(
        2,
        i -> {
          if (i == 0) admin.deleteAuction(a.id());
          else
            try {
              bid(a.id(), bidder.id(), "100");
            } catch (BusinessException e) {
              assertEquals(404, e.status.value());
            }
        });
    assertFalse(auctionRepo.existsById(a.id()));
    assertEquals(0, bids.count());
  }

  @Test
  void accountDeletionRacingBidRecalculatesConsistently() throws Exception {
    var administrator = administrator();
    var a = auction(account().id());
    var first = account();
    var deleted = account();
    bid(a.id(), first.id(), "100");
    race(
        2,
        i -> {
          if (i == 0) admin.deleteUser(administrator.id(), deleted.id());
          else
            try {
              bid(a.id(), deleted.id(), "110");
            } catch (BusinessException e) {
              assertEquals(404, e.status.value());
            }
        });
    var r = auctions.get(a.id());
    assertEquals(first.id(), r.winnerUserId());
    assertEquals(1, bids.count());
    assertEquals(1, bids.findByAuctionIdAndStatus(a.id(), "WINNING").size());
    assertFalse(users.existsById(deleted.id()));
  }

  @Test
  void independentAuctionsAcceptSameUsersConcurrently() throws Exception {
    var seller = account();
    var bidder = account();
    var ids = List.of(auction(seller.id()).id(), auction(seller.id()).id());
    race(2, i -> bid(ids.get(i), bidder.id(), "100"));
    assertEquals(2, bids.count());
  }
}
