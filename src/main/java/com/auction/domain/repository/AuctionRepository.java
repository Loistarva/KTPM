package com.auction.domain.repository;
import com.auction.domain.model.Auction;
import java.util.List;

public interface AuctionRepository {
    Auction findById(Long id);
    List<Auction> findAll();
    Auction save(Auction auction);
    void deleteById(Long id);
}