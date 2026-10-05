package com.ktpm.common;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseModel {
  private Long id;
  private Instant createdAt;
  private Instant updatedAt;
}
