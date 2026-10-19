package com.ktpm.realtime.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@ConditionalOnProperty(name = "ktpm.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionEventHub {
  private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
  private final ExecutorService senders;
  private final int maxClients;
  private final MeterRegistry metrics;
  private volatile boolean databaseOnline;

  public AuctionEventHub(
      @Value("${ktpm.realtime.max-clients:200}") int maxClients, MeterRegistry metrics) {
    this.maxClients = maxClients;
    this.metrics = metrics;
    senders =
        new ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1024),
            r -> new Thread(r, "sse-sender"));
    metrics.gauge("ktpm.sse.clients", clients, Set::size);
  }

  public synchronized SseEmitter subscribe() {
    if (clients.size() >= maxClients)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Realtime is busy; retry shortly");
    SseEmitter emitter = new SseEmitter(120000L);
    clients.add(emitter);
    emitter.onCompletion(() -> clients.remove(emitter));
    emitter.onTimeout(
        () -> {
          clients.remove(emitter);
          emitter.complete();
        });
    emitter.onError(e -> clients.remove(emitter));
    send(emitter, databaseOnline ? "resync" : "degraded", Map.of("reason", "connected"));
    return emitter;
  }

  public void changed(String auctionId) {
    broadcast("auction-changed", Map.of("auctionId", auctionId));
  }

  public void resync() {
    databaseOnline = true;
    broadcast("resync", Map.of("reason", "database-reconnected"));
  }

  public void degraded() {
    databaseOnline = false;
    broadcast("degraded", Map.of("reason", "database-unavailable"));
  }

  private void broadcast(String event, Object data) {
    for (var emitter : clients) send(emitter, event, data);
  }

  @Scheduled(fixedDelay = 15000)
  public void heartbeat() {
    broadcast("heartbeat", Map.of("online", databaseOnline));
  }

  private void send(SseEmitter emitter, String event, Object data) {
    try {
      senders.execute(
          () -> {
            if (!clients.contains(emitter)) return;
            try {
              emitter.send(SseEmitter.event().name(event).data(data).reconnectTime(2000));
            } catch (Exception e) {
              disconnect(emitter);
            }
          });
    } catch (RejectedExecutionException e) {
      metrics.counter("ktpm.sse.slow-clients").increment();
      disconnect(emitter);
    }
  }

  private void disconnect(SseEmitter emitter) {
    clients.remove(emitter);
    // Completion causes EventSource to reconnect and fetch the canonical state.
    emitter.complete();
  }

  @PreDestroy
  public void stop() {
    for (var emitter : clients) emitter.complete();
    clients.clear();
    senders.shutdownNow();
  }
}
