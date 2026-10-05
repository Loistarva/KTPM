package com.ktpm.bidding.domain;

import com.ktpm.common.BaseModel;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Bid extends BaseModel {

  private Long auctionId;

  private Long bidderId;

  private BigDecimal amount;

  private String status = "WINNING";
}
