package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktpm.common.infrastructure.RequestAdmissionFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestAdmissionTest {
  @Test
  void overloadIsRejectedImmediatelyAndCapacityIsRestored() throws Exception {
    var filter =
        new RequestAdmissionFilter(
            1, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var busy =
          executor.submit(
              () -> {
                try {
                  filter.doFilter(
                      new MockHttpServletRequest("GET", "/api/auctions"),
                      new MockHttpServletResponse(),
                      (r, s) -> {
                        entered.countDown();
                        try {
                          assertTrue(release.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                      });
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      var rejected = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("POST", "/api/auctions/1/bids"),
          rejected,
          (r, s) -> fail("Capacity exceeded"));
      assertEquals(503, rejected.getStatus());
      assertEquals("1", rejected.getHeader("Retry-After"));
      release.countDown();
      busy.get(3, TimeUnit.SECONDS);
      var accepted = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/auctions"),
          accepted,
          (r, s) -> ((MockHttpServletResponse) s).setStatus(204));
      assertEquals(204, accepted.getStatus());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
