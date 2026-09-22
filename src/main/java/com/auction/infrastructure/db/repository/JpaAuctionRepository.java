package com.auction.infrastructure.db.repository;
import com.auction.infrastructure.db.entity.AuctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuctionRepository extends JpaRepository<AuctionEntity, Long> {
}