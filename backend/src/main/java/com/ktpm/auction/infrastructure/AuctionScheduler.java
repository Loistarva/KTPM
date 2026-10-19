package com.ktpm.auction.infrastructure;

import com.ktpm.auction.application.AuctionLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ktpm.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionScheduler {
  private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);
  private final AuctionJobQueue jobs;
  private final AuctionLifecycleService lifecycle;
  private final MeterRegistry metrics;
  private final ExecutorService workers;
  private final Semaphore capacity;
  private final int batchSize;
  private final Duration lease;

  public AuctionScheduler(
      AuctionJobQueue jobs,
      AuctionLifecycleService lifecycle,
      MeterRegistry metrics,
      @Value("${ktpm.scheduler.workers:4}") int count,
      @Value("${ktpm.scheduler.batch-size:64}") int batchSize,
      @Value("${ktpm.scheduler.lease-seconds:60}") long leaseSeconds) {
    if (count < 1 || batchSize < count || leaseSeconds < 1)
      throw new IllegalArgumentException("Invalid worker limits");
    this.jobs = jobs;
    this.lifecycle = lifecycle;
    this.metrics = metrics;
    this.batchSize = batchSize;
    this.lease = Duration.ofSeconds(leaseSeconds);
    this.capacity = new Semaphore(batchSize);
    this.workers =
        new ThreadPoolExecutor(
            count,
            count,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(batchSize),
            r -> new Thread(r, "auction-worker"));
    metrics.gauge("ktpm.scheduler.inflight", capacity, s -> batchSize - s.availablePermits());
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    tick();
  }

  @Scheduled(fixedDelayString = "${ktpm.scheduler.delay-ms:500}")
  public synchronized void tick() {
    int available = Math.min(batchSize, capacity.availablePermits());
    if (available == 0 || workers.isShutdown()) return;
    try {
      for (var claim : jobs.claim(available, lease)) {
        capacity.acquireUninterruptibly();
        try {
          workers.execute(() -> process(claim));
        } catch (RejectedExecutionException e) {
          capacity.release();
          jobs.retry(claim);
        }
      }
    } catch (Exception e) {
      metrics.counter("ktpm.scheduler.failures", "stage", "claim").increment();
      log.warn("Unable to claim due auctions; retry next tick", e);
    }
  }

  private void process(AuctionJobQueue.Claim claim) {
    long start = System.nanoTime();
    try {
      lifecycle.advanceClaimed(claim.auctionId(), claim.token());
      metrics.counter("ktpm.scheduler.processed").increment();
    } catch (Exception e) {
      metrics.counter("ktpm.scheduler.failures", "stage", "advance").increment();
      log.warn("Auction {} failed; retry with backoff", claim.auctionId(), e);
      try {
        jobs.retry(claim);
      } catch (Exception retryFailure) {
        log.warn("Unable to release claim; lease will expire", retryFailure);
      }
    } finally {
      metrics
          .timer("ktpm.scheduler.execution")
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      capacity.release();
    }
  }

  @PreDestroy
  public void stop() throws InterruptedException {
    workers.shutdown();
    if (!workers.awaitTermination(10, TimeUnit.SECONDS)) workers.shutdownNow();
  }
}
