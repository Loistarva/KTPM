package com.auction.domain.model;

public class Auction {
    private Long id;
    private String title;
    private Double currentHighestBid;
    private Long ownerId;

    public Auction(Long id, String title, Double startingPrice, Long ownerId) {
        this.id = id;
        this.title = title;
        this.currentHighestBid = startingPrice;
        this.ownerId = ownerId;
    }

    // Nghiệp vụ: Đặt giá thầu
    public void placeBid(Double amount) {
        if (amount == null || amount <= this.currentHighestBid) {
            throw new IllegalArgumentException("Giá thầu mới phải cao hơn giá hiện tại (" + this.currentHighestBid + ")");
        }
        this.currentHighestBid = amount;
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    // Getters...
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Double getCurrentHighestBid() { return currentHighestBid; }
    public Long getOwnerId() { return ownerId; }
}
