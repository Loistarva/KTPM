package com.ktpm.auction.api;

import com.ktpm.auction.application.AuctionService;
import com.ktpm.auth.infrastructure.CurrentUser;
import com.ktpm.common.Pages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {
  private final AuctionService auctions;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ResponseEntity<com.ktpm.auction.application.AuctionModels.AuctionResponse> create(
      @Valid @RequestBody AuctionDtos.CreateAuctionRequest r) {
    return ResponseEntity.status(201).body(auctions.create(CurrentUser.get().id(), r.command()));
  }

  @GetMapping
  public Pages.Result<com.ktpm.auction.application.AuctionModels.AuctionResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return auctions.list(status, search, page, size);
  }

  @GetMapping("/me")
  public Pages.Result<com.ktpm.auction.application.AuctionModels.AuctionResponse> mine(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return auctions.mine(CurrentUser.get().id(), page, size);
  }

  @GetMapping("/{id}")
  public com.ktpm.auction.application.AuctionModels.AuctionResponse get(@PathVariable Long id) {
    return auctions.get(id);
  }

  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    auctions.delete(CurrentUser.get().id(), id);
    return ResponseEntity.noContent().build();
  }
}
