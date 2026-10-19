package com.ktpm.common.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktpm.common.ApiExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject before JWT/database work when an instance has no API capacity. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestAdmissionFilter extends OncePerRequestFilter {
  private final Semaphore slots;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  public RequestAdmissionFilter(
      @Value("${ktpm.admission.max-concurrent:48}") int max,
      ObjectMapper json,
      MeterRegistry metrics) {
    if (max < 1) throw new IllegalArgumentException("API concurrency must be positive");
    slots = new Semaphore(max);
    this.json = json;
    this.metrics = metrics;
    metrics.gauge("ktpm.api.available", slots, Semaphore::availablePermits);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!slots.tryAcquire()) {
      metrics.counter("ktpm.api.rejected").increment();
      response.setStatus(503);
      response.setHeader("Retry-After", "1");
      response.setContentType("application/json");
      json.writeValue(
          response.getOutputStream(),
          ApiExceptionHandler.body(
              HttpStatus.SERVICE_UNAVAILABLE,
              "Server is busy; retry shortly",
              request.getRequestURI()));
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      slots.release();
    }
  }
}
