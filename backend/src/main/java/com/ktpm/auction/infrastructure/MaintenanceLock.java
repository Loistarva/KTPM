package com.ktpm.auction.infrastructure;

import com.ktpm.auction.domain.MaintenanceGate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Normal auction writes run concurrently; destructive cleanup waits for all of them. */
@Component
@RequiredArgsConstructor
public class MaintenanceLock implements MaintenanceGate {
  private final JdbcTemplate jdbc;

  @Transactional(propagation = Propagation.MANDATORY)
  public void shared() {
    jdbc.execute("SELECT pg_advisory_xact_lock_shared(7312026)");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void exclusive() {
    jdbc.execute("SELECT pg_advisory_xact_lock(7312026)");
  }
}
