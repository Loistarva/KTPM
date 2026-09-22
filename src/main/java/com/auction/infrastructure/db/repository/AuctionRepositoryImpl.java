package com.auction.infrastructure.db.repository;
import com.auction.domain.model.Auction;
import com.auction.domain.repository.AuctionRepository;
import com.auction.infrastructure.db.entity.AuctionEntity;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AuctionRepositoryImpl implements AuctionRepository {

    private final JpaAuctionRepository jpaRepo;

    public AuctionRepositoryImpl(JpaAuctionRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    // Data Mapper thủ công: Entity -> Domain
    private Auction toDomain(AuctionEntity entity) {
        return new Auction(entity.getId(), entity.getTitle(), entity.getCurrentHighestBid(), entity.getOwnerId());
    }

    // Data Mapper thủ công: Domain -> Entity
    private AuctionEntity toEntity(Auction domain) {
        return new AuctionEntity(domain.getId(), domain.getTitle(), domain.getCurrentHighestBid(), domain.getOwnerId());
    }

    @Override
    public Auction findById(Long id) {
        return jpaRepo.findById(id).map(this::toDomain).orElse(null);
    }

    @Override
    public List<Auction> findAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Auction save(Auction auction) {
        AuctionEntity savedEntity = jpaRepo.save(toEntity(auction));
        return toDomain(savedEntity);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepo.deleteById(id);
    }
}