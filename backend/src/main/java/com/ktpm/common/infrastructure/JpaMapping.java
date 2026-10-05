package com.ktpm.common.infrastructure;

import com.ktpm.common.BaseModel;
import java.util.function.Supplier;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.JpaRepository;

public final class JpaMapping {
  private JpaMapping() {}

  public static <M> M model(Object entity, Supplier<M> factory) {
    M m = factory.get();
    BeanUtils.copyProperties(entity, m);
    return m;
  }

  public static <E extends BaseEntity, M extends BaseModel> M save(
      M model, JpaRepository<E, Long> repo, Supplier<E> factory) {
    E entity = model.getId() == null ? factory.get() : repo.findById(model.getId()).orElseThrow();
    BeanUtils.copyProperties(model, entity);
    entity = repo.saveAndFlush(entity);
    BeanUtils.copyProperties(entity, model);
    return model;
  }
}
