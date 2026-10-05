package com.ktpm.common.infrastructure;

import com.ktpm.common.Pages;
import java.util.function.Function;
import org.springframework.data.domain.*;

public final class JpaPages {
  private JpaPages() {}

  public static Pageable request(int page, int size) {
    Pages.validate(page, size);
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
  }

  public static <E, M> Pages.Result<M> result(Page<E> p, Function<E, M> mapper) {
    return new Pages.Result<>(
        p.getContent().stream().map(mapper).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }
}
