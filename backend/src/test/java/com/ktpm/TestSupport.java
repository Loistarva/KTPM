package com.ktpm;

import com.ktpm.admin.application.AdminService;
import com.ktpm.auction.application.*;
import com.ktpm.auction.application.AuctionModels.*;
import com.ktpm.auction.domain.AuctionStore;
import com.ktpm.auth.application.AuthModels.*;
import com.ktpm.auth.application.AuthService;
import com.ktpm.bidding.application.BiddingService;
import com.ktpm.bidding.domain.BidStore;
import com.ktpm.user.application.UserDtos.Profile;
import com.ktpm.user.domain.UserStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class TestSupport extends DatabaseTest {
  @Autowired protected AuthService auth;
  @Autowired protected AuctionService auctions;
  @Autowired protected AuctionLifecycleService lifecycle;
  @Autowired protected AuctionStore auctionRepo;
  @Autowired protected BiddingService bidding;
  @Autowired protected BidStore bids;
  @Autowired protected AdminService admin;
  @Autowired protected UserStore users;
  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  protected static final AtomicInteger names = new AtomicInteger();

  @BeforeEach
  void reset() {
    jdbc.execute("TRUNCATE bids,auctions,users RESTART IDENTITY CASCADE");
  }

  protected Profile account() {
    String name = "user" + names.incrementAndGet();
    return auth.register(new Register(name, name + "@example.com", "Password123!"));
  }

  protected Profile administrator() {
    var p = account();
    var u = users.findById(p.id()).orElseThrow();
    u.setRole("ADMIN");
    users.saveAndFlush(u);
    return Profile.from(u);
  }

  protected String token(Profile p) {
    return auth.login(new Login(p.username(), "Password123!")).accessToken();
  }

  protected AuctionResponse auction(Long seller) {
    return auctions.create(
        seller,
        new CreateAuctionRequest(
            "Laptop",
            "A useful laptop",
            "USED",
            null,
            new BigDecimal("100"),
            new BigDecimal("10"),
            Instant.now().minusSeconds(2),
            Instant.now().plusSeconds(600)));
  }

  protected void bid(Long auction, Long user, String amount) {
    bidding.bid(auction, user, new BigDecimal(amount));
  }
}
