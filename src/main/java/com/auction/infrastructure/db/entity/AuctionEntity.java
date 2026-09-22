package com.auction.infrastructure.db.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "auctions")
public class AuctionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private Double currentHighestBid;
    private Long ownerId;

    // Default constructor cho JPA
    public AuctionEntity() {}

    public AuctionEntity(Long id, String title, Double currentHighestBid, Long ownerId) {
        this.id = id;
        this.title = title;
        this.currentHighestBid = currentHighestBid;
        this.ownerId = ownerId;
    }
    // Getters và Setters...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(Double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}