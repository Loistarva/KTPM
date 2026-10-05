package com.ktpm.auction.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository
    extends JpaRepository<AuctionEntity, Long>, JpaSpecificationExecutor<AuctionEntity> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AuctionEntity a where a.id=:id")
  Optional<AuctionEntity> lockById(@Param("id") Long id);

  Page<AuctionEntity> findBySellerId(Long seller, Pageable page);

  @Query(
      "select a.id from AuctionEntity a where (a.status='SCHEDULED' and a.startingTime<=:now) or (a.status='ACTIVE' and a.endingTime<=:now) order by a.id")
  List<Long> findDue(@Param("now") java.time.Instant now);
}
