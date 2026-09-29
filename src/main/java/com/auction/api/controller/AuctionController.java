package com.auction.api.controller;
import com.auction.api.dto.request.BidRequestDto;
import com.auction.domain.model.Auction;
import com.auction.domain.service.AuctionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.auction.api.dto.request.AuctionCreateRequestDto;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    // GET: Public (Không cần đăng nhập)
    @GetMapping
    public ResponseEntity<List<Auction>> getAllAuctions() {
        return ResponseEntity.ok(auctionService.getAllAuctions());
    }

    // POST: Cần xác thực (Xác thực thực hiện ở Filter, Controller chỉ lấy request ra)
    @PostMapping("/{id}/bids")
    public ResponseEntity<String> placeBid(@PathVariable Long id, @RequestBody BidRequestDto request) {
        try {
            auctionService.placeBid(id, request.getAmount());
            return ResponseEntity.ok("Đặt giá thầu thành công!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST: Cần xác thực (Tạo phiên đấu giá mới)
    @PostMapping
    public ResponseEntity<?> createAuction(@RequestBody AuctionCreateRequestDto request, @RequestAttribute("userId") Long userId) {
        Auction created = auctionService.createAuction(request.getTitle(), request.getStartingPrice(), userId);
        return ResponseEntity.status(201).body(created); // 201 Created
    }

    // DELETE: Cần xác thực
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAuction(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        try {
            auctionService.deleteAuction(id, userId);
            return ResponseEntity.ok("Xóa thành công.");
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}