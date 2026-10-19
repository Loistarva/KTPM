package com.ktpm.bidding.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BidRepository extends JpaRepository<BidEntity, Long> {
  @Modifying
  @Query(
      "update BidEntity b set b.status='OUTBID', b.updatedAt=CURRENT_TIMESTAMP where b.id=:id and b.status='WINNING'")
  int outbid(@org.springframework.data.repository.query.Param("id") Long id);

  @Modifying
  @Query(
      "update BidEntity b set b.status=case when b.status='WINNING' then :winnerStatus else 'LOST' end, b.updatedAt=CURRENT_TIMESTAMP where b.auctionId=:id and b.status in ('WINNING','OUTBID')")
  int closeAuction(
      @org.springframework.data.repository.query.Param("id") Long id,
      @org.springframework.data.repository.query.Param("winnerStatus") String winnerStatus);

  List<BidEntity> findByAuctionIdOrderByAmountDescIdDesc(Long auctionId);

  @Query("select distinct b.auctionId from BidEntity b where b.bidderId=:id order by b.auctionId")
  List<Long> affected(@org.springframework.data.repository.query.Param("id") Long id);

  Page<BidEntity> findByAuctionId(Long auctionId, Pageable pageable);

  Page<BidEntity> findByBidderId(Long bidderId, Pageable pageable);

  List<BidEntity> findByAuctionIdAndStatus(Long auctionId, String status);
}
