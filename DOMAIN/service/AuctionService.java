package com.auction.domain.service;
import com.auction.domain.model.Auction;
import com.auction.domain.repository.AuctionRepository;
import java.util.List;

// Chú ý: Không có @Service ở đây
public class AuctionService {
    private final AuctionRepository auctionRepository;

    public AuctionService(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    public List<Auction> getAllAuctions() {
        return auctionRepository.findAll();
    }

    public void placeBid(Long auctionId, Double amount) {
        Auction auction = auctionRepository.findById(auctionId);
        if (auction == null) throw new RuntimeException("Không tìm thấy phiên đấu giá");

        auction.placeBid(amount); // Logic nằm ở Model
        auctionRepository.save(auction);
    }

    public void deleteAuction(Long auctionId, Long userId) {
        Auction auction = auctionRepository.findById(auctionId);
        if (auction == null) throw new RuntimeException("Không tìm thấy phiên đấu giá");

        if (!auction.isOwnedBy(userId)) {
            throw new RuntimeException("Bạn không có quyền xóa phiên đấu giá này");
        }
        auctionRepository.deleteById(auctionId);
    }
}