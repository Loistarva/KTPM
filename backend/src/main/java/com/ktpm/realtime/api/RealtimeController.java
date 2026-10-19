package com.ktpm.realtime.api;

import com.ktpm.realtime.infrastructure.AuctionEventHub;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ktpm.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeController {
  private final AuctionEventHub hub;

  @GetMapping(value = "/api/events", produces = "text/event-stream")
  public SseEmitter events(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-cache, no-transform");
    response.setHeader("X-Accel-Buffering", "no");
    return hub.subscribe();
  }
}
