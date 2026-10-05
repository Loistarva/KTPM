package com.ktpm.bidding.infrastructure;

import com.ktpm.common.infrastructure.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bids")
@Getter
@Setter
public class BidEntity extends BaseEntity {
  @Column(nullable = false)
  private Long auctionId;

  @Column(nullable = false)
  private Long bidderId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 16)
  private String status = "WINNING";
}
