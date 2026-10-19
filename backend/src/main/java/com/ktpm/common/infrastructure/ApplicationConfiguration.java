package com.ktpm.common.infrastructure;

import com.ktpm.admin.application.AdminService;
import com.ktpm.auction.application.*;
import com.ktpm.auction.domain.*;
import com.ktpm.auth.application.*;
import com.ktpm.bidding.application.BiddingService;
import com.ktpm.bidding.domain.BidStore;
import com.ktpm.common.ContentionObserver;
import com.ktpm.common.UnitOfWork;
import com.ktpm.user.application.UserService;
import com.ktpm.user.domain.UserStore;
import org.springframework.context.annotation.*;

@Configuration
public class ApplicationConfiguration {
  @Bean
  UserService userService(UserStore u, UnitOfWork w) {
    return new UserService(u, w);
  }

  @Bean
  AuthService authService(UserStore u, Passwords p, AuthTokens t, UnitOfWork w) {
    return new AuthService(u, p, t, w);
  }

  @Bean
  AuctionService auctionService(AuctionStore a, UserService u, MaintenanceGate g, UnitOfWork w) {
    return new AuctionService(a, u, g, w);
  }

  @Bean
  BiddingService biddingService(
      AuctionStore s,
      MaintenanceGate g,
      UserService u,
      BidStore b,
      UnitOfWork w,
      ContentionObserver c) {
    return new BiddingService(s, g, u, b, w, c);
  }

  @Bean
  AuctionLifecycleService lifecycle(
      BiddingService b, MaintenanceGate g, AuctionStore s, UnitOfWork w) {
    return new AuctionLifecycleService(b, g, s, w);
  }

  @Bean
  AdminService adminService(
      UserStore u, AuctionStore a, BidStore b, MaintenanceGate g, UnitOfWork w) {
    return new AdminService(u, a, b, g, w);
  }
}
