package com.ktpm.auction.infrastructure;

import com.ktpm.common.infrastructure.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "auctions")
@Getter
@Setter
public class AuctionEntity extends BaseEntity {
  @Column(nullable = false)
  private Long sellerId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 5000)
  private String description;

  @Column(nullable = false, length = 50)
  private String condition;

  @Column(length = 2000)
  private String imageUrl;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal startingPrice;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal currentPrice;

  @Column(precision = 19, scale = 2)
  private BigDecimal finalPrice;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal minimumBidStep;

  @Column(nullable = false)
  private Instant startingTime;

  @Column(nullable = false)
  private Instant endingTime;

  @Column(nullable = false, length = 16)
  private String status = "SCHEDULED";

  private Long winnerUserId;
  private Long winningBidId;
}
