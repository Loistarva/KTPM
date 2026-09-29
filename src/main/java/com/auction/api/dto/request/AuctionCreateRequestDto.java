package com.auction.api.dto.request;

public class AuctionCreateRequestDto {
    private String title;
    private Double startingPrice;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(Double startingPrice) { this.startingPrice = startingPrice; }
}