package com.ktpm.common.infrastructure;

import com.ktpm.common.ContentionObserver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidContentionMetrics implements ContentionObserver {
  private final MeterRegistry metrics;

  public void bidRetry() {
    metrics.counter("ktpm.bid.retries").increment();
  }
}
