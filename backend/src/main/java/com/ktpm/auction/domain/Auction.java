package com.ktpm.auction.domain;

import com.ktpm.common.BaseModel;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
public class Auction extends BaseModel {

  private Long sellerId;

  private String name;

  private String description;

  private String condition;

  private String imageUrl;

  private BigDecimal startingPrice;

  private BigDecimal currentPrice;

  private BigDecimal finalPrice;

  private BigDecimal minimumBidStep;

  private Instant startingTime;

  private Instant endingTime;

  private String status = "SCHEDULED";

  private Long winnerUserId;
  private Long winningBidId;
}
