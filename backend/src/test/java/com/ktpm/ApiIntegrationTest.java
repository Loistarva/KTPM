package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.ktpm.auth.application.AuthModels.Login;
import com.ktpm.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ApiIntegrationTest extends TestSupport {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void failedAuctionWriteRollsBackBidAndPreviousLeader() {
    var a = auction(account().id());
    var first = account();
    var second = account();
    bid(a.id(), first.id(), "100");
    jdbc.execute(
        "CREATE FUNCTION test_reject_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.current_price=110 THEN RAISE EXCEPTION 'forced rollback'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER test_reject_update BEFORE UPDATE ON auctions FOR EACH ROW EXECUTE FUNCTION test_reject_update()");
    try {
      assertThrows(RuntimeException.class, () -> bid(a.id(), second.id(), "110"));
    } finally {
      jdbc.execute("DROP TRIGGER test_reject_update ON auctions");
      jdbc.execute("DROP FUNCTION test_reject_update()");
    }
    var result = auctions.get(a.id());
    assertEquals(first.id(), result.winnerUserId());
    assertEquals(0, result.currentPrice().compareTo(new java.math.BigDecimal("100")));
    assertEquals(1, bids.count());
    assertEquals("WINNING", bids.findById(result.winningBidId()).orElseThrow().getStatus());
  }

  @Test
  void reusedDatabaseIdDoesNotReviveOldToken() throws Exception {
    var original = account();
    var old = users.findById(original.id()).orElseThrow();
    old.setTokenVersion(0);
    users.saveAndFlush(old);
    var stale = token(original);
    jdbc.execute("TRUNCATE bids,auctions,users RESTART IDENTITY CASCADE");
    var replacement = account();
    assertEquals(original.id(), replacement.id());
    assertTrue(users.findById(replacement.id()).orElseThrow().getTokenVersion() > 0);
    mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + stale))
        .andExpect(status().isUnauthorized());
  }

  private String createBody(String price, String url) {
    return "{\"name\":\"Camera\",\"description\":\"A camera\",\"condition\":\"USED\",\"imageUrl\":"
        + url
        + ",\"startingPrice\":\""
        + price
        + "\",\"minimumBidStep\":\"0.10\",\"startingTime\":\""
        + java.time.Instant.now().minusSeconds(2)
        + "\",\"endingTime\":\""
        + java.time.Instant.now().plusSeconds(600)
        + "\"}";
  }

  @Test
  void sessionAndLogoutRevocation() throws Exception {
    var p = account();
    var t = token(p);
    mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(p.username()))
        .andExpect(jsonPath("$.fullName").doesNotExist());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auth/logout").header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
    mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + t))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createIncludesItemAndAtMostOneImage() throws Exception {
    var t = token(account());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auctions")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(createBody("0.10", "\"https://example.com/item.png\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Camera"))
        .andExpect(jsonPath("$.imageUrl").value("https://example.com/item.png"))
        .andExpect(jsonPath("$.productId").doesNotExist())
        .andExpect(jsonPath("$.reservePrice").doesNotExist());
  }

  @Test
  void priceAndImageValidation() throws Exception {
    var t = token(account());
    for (String p : List.of("0", "1.001", "100000000000000000.00"))
      mvc.perform(
              MockMvcRequestBuilders.post("/api/auctions")
                  .header("Authorization", "Bearer " + t)
                  .contentType("application/json")
                  .content(createBody(p, "null")))
          .andExpect(status().isBadRequest());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auctions")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(createBody("1", "\"javascript:alert(1)\"")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auctions")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(createBody("99999999999999999.99", "null")))
        .andExpect(status().isCreated());
  }

  @Test
  void schemaAndOpenApiOnlyContainCurrentFeatures() throws Exception {
    var tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'",
            String.class);
    assertEquals(
        Set.of("users", "auctions", "bids", "flyway_schema_history"), new HashSet<>(tables));
    var body =
        mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var paths = json.readTree(body).get("paths");
    int count = 0;
    for (var entries = paths.fields(); entries.hasNext(); ) {
      var entry = entries.next();
      assertFalse(entry.getKey().contains("product"));
      assertFalse(entry.getKey().contains("categor"));
      for (var methods = entry.getValue().fieldNames(); methods.hasNext(); )
        if (Set.of("get", "post", "delete").contains(methods.next())) count++;
    }
    assertEquals(16, count);
  }

  @Test
  void publicReadsProtectedWritesAndAdminPermissions() throws Exception {
    var seller = account();
    var a = auction(seller.id());
    mvc.perform(MockMvcRequestBuilders.get("/api/auctions/" + a.id())).andExpect(status().isOk());
    mvc.perform(MockMvcRequestBuilders.get("/api/auctions/me"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/auctions/" + a.id())
                .header("Authorization", "Bearer " + token(seller)))
        .andExpect(status().isForbidden());
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/auctions/" + a.id())
                .header("Authorization", "Bearer " + token(account())))
        .andExpect(status().isForbidden());
  }

  @Test
  void searchLiteralWildcardsAndPagination() {
    var seller = account();
    auction(seller.id());
    assertEquals(1, auctions.list(null, "laptop", 0, 20).totalElements());
    assertEquals(0, auctions.list(null, "%", 0, 20).totalElements());
    assertThrows(BusinessException.class, () -> auctions.list(null, null, 0, 101));
  }

  @Test
  void sellerDeleteWithoutBidAndAdminDeleteWithBid() throws Exception {
    var seller = account();
    var a = auction(seller.id());
    var t = token(seller);
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/auctions/" + a.id())
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
    assertFalse(auctionRepo.existsById(a.id()));
    var other = auction(seller.id());
    bid(other.id(), account().id(), "100");
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/auctions/" + other.id())
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isConflict());
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/auctions/" + other.id())
                .header("Authorization", "Bearer " + token(administrator())))
        .andExpect(status().isNoContent());
    assertEquals(0, bids.count());
    assertFalse(auctionRepo.existsById(other.id()));
  }

  @Test
  void deleteUserRemovesOwnedAuctionsAndRecalculatesActiveLeader() throws Exception {
    var administrator = administrator();
    var seller = account();
    var previous = account();
    var deleted = account();
    var a = auction(seller.id());
    var owned = auction(deleted.id());
    bid(owned.id(), previous.id(), "100");
    bid(a.id(), previous.id(), "100");
    bid(a.id(), deleted.id(), "110");
    String stale = token(deleted);
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/users/" + deleted.id())
                .header("Authorization", "Bearer " + token(administrator)))
        .andExpect(status().isNoContent());
    assertFalse(users.existsById(deleted.id()));
    assertFalse(auctionRepo.existsById(owned.id()));
    var result = auctions.get(a.id());
    assertEquals(previous.id(), result.winnerUserId());
    assertEquals(0, result.currentPrice().compareTo(new java.math.BigDecimal("100")));
    assertEquals("WINNING", bids.findById(result.winningBidId()).orElseThrow().getStatus());
    mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + stale))
        .andExpect(status().isUnauthorized());
    assertThrows(
        BusinessException.class, () -> auth.login(new Login(deleted.username(), "Password123!")));
  }

  @Test
  void deletingWinnerRecalculatesEndedResultAndLastBidMakesFailed() {
    var administrator = administrator();
    var a = auction(account().id());
    var first = account();
    var last = account();
    bid(a.id(), first.id(), "100");
    bid(a.id(), last.id(), "110");
    lifecycle.advance(a.id(), a.endingTime().plusSeconds(1));
    admin.deleteUser(administrator.id(), last.id());
    var result = auctions.get(a.id());
    assertEquals("ENDED", result.status());
    assertEquals(first.id(), result.winnerUserId());
    assertEquals(0, result.finalPrice().compareTo(new java.math.BigDecimal("100")));
    assertEquals("WON", bids.findById(result.winningBidId()).orElseThrow().getStatus());
    admin.deleteUser(administrator.id(), first.id());
    result = auctions.get(a.id());
    assertEquals("FAILED", result.status());
    assertNull(result.winnerUserId());
    assertNull(result.finalPrice());
    assertNull(result.winningBidId());
    assertEquals(0, result.currentPrice().compareTo(result.startingPrice()));
  }

  @Test
  void deletionProtectsSelfAndIsAtomicOnMissingTarget() {
    var administrator = administrator();
    var a = auction(account().id());
    bid(a.id(), account().id(), "100");
    assertThrows(
        BusinessException.class, () -> admin.deleteUser(administrator.id(), administrator.id()));
    assertTrue(users.existsById(administrator.id()));
    assertThrows(BusinessException.class, () -> admin.deleteAuction(99999L));
    assertEquals(1, bids.count());
  }

  @Test
  void removedApisHaveNoControllers() throws Exception {
    var t = token(administrator());
    for (String path :
        List.of(
            "/api/products",
            "/api/categories",
            "/api/users/me",
            "/api/users/me/wallet",
            "/api/admin/categories"))
      mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + t))
          .andExpect(status().isNotFound());
  }
}
