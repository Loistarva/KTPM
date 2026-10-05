package com.ktpm.auction.infrastructure;

import com.ktpm.auction.application.AuctionLifecycleService;
import com.ktpm.auction.application.AuctionService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ktpm.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionScheduler {
  private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);
  private final AuctionService auctions;
  private final AuctionLifecycleService lifecycle;

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    tick();
  }

  @Scheduled(fixedDelayString = "${ktpm.scheduler.delay-ms:5000}")
  public void tick() {
    Instant now = Instant.now();
    for (Long id : auctions.due(now)) {
      try {
        lifecycle.advance(id, now);
      } catch (Exception e) {
        log.error("Unable to advance auction {}; retry next tick", id, e);
      }
    }
  }
}
