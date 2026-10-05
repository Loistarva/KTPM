package com.ktpm.bidding.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BidRepository extends JpaRepository<BidEntity, Long> {
  List<BidEntity> findByAuctionIdOrderByAmountDescIdDesc(Long auctionId);

  @Query("select distinct b.auctionId from BidEntity b where b.bidderId=:id order by b.auctionId")
  List<Long> affected(@org.springframework.data.repository.query.Param("id") Long id);

  Page<BidEntity> findByAuctionId(Long auctionId, Pageable pageable);

  Page<BidEntity> findByBidderId(Long bidderId, Pageable pageable);

  List<BidEntity> findByAuctionIdAndStatus(Long auctionId, String status);
}
