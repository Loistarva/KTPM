package com.ktpm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ktpm.common.ApiExceptionHandler;
import com.ktpm.realtime.api.RealtimeController;
import com.ktpm.realtime.infrastructure.AuctionEventHub;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RealtimeDeliveryTest {
  @Test
  void realtimeCapacityReturns503AndRetryAfter() throws Exception {
    var hub = new AuctionEventHub(1, new SimpleMeterRegistry());
    try {
      var mvc =
          MockMvcBuilders.standaloneSetup(new RealtimeController(hub))
              .setControllerAdvice(new ApiExceptionHandler())
              .build();
      mvc.perform(get("/api/events")).andExpect(request().asyncStarted());
      mvc.perform(get("/api/events"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(header().string("Retry-After", "1"));
    } finally {
      hub.stop();
    }
  }
}
