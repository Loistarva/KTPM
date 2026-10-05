package com.ktpm.bidding.api;

import com.ktpm.auth.infrastructure.CurrentUser;
import com.ktpm.bidding.application.BiddingService;
import com.ktpm.common.Pages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BiddingController {
  private final BiddingService bidding;

  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.CREATED)
  @PostMapping("/api/auctions/{id}/bids")
  public ResponseEntity<com.ktpm.bidding.application.BiddingModels.BidResponse> bid(
      @PathVariable Long id, @Valid @RequestBody BiddingDtos.PlaceBid r) {
    return ResponseEntity.status(201).body(bidding.bid(id, CurrentUser.get().id(), r.amount()));
  }

  @GetMapping("/api/auctions/{id}/bids")
  public Pages.Result<com.ktpm.bidding.application.BiddingModels.BidResponse> history(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return bidding.history(id, page, size);
  }

  @GetMapping("/api/users/me/bids")
  public Pages.Result<com.ktpm.bidding.application.BiddingModels.BidResponse> mine(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return bidding.mine(CurrentUser.get().id(), page, size);
  }
}
