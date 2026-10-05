package com.ktpm.admin.api;

import com.ktpm.admin.application.AdminService;
import com.ktpm.auction.application.AuctionModels.AuctionResponse;
import com.ktpm.auction.application.AuctionService;
import com.ktpm.auth.infrastructure.CurrentUser;
import com.ktpm.common.Pages;
import com.ktpm.user.application.UserDtos.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  private final AdminService admin;
  private final AuctionService auctions;

  @GetMapping("/users")
  public Pages.Result<Profile> users(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return admin.users(page, size);
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    admin.deleteUser(CurrentUser.get().id(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/auctions")
  public Pages.Result<AuctionResponse> auctions(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return auctions.list(status, search, page, size);
  }

  @DeleteMapping("/auctions/{id}")
  public ResponseEntity<Void> deleteAuction(@PathVariable Long id) {
    admin.deleteAuction(id);
    return ResponseEntity.noContent().build();
  }
}
