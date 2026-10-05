package com.ktpm.common;

import java.util.List;
import java.util.function.Function;

public final class Pages {
  private Pages() {}

  public record Result<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}

  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw BusinessException.bad("page >= 0 and size between 1 and 100 required");
  }

  public static <A, B> Result<B> map(Result<A> page, Function<A, B> mapper) {
    return new Result<>(
        page.content().stream().map(mapper).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
