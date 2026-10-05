package com.ktpm.common;

import java.util.function.Supplier;

public interface UnitOfWork {
  <T> T read(Supplier<T> action);

  <T> T write(Supplier<T> action);

  default void write(Runnable action) {
    write(
        () -> {
          action.run();
          return null;
        });
  }

  void mandatory(Runnable action);
}
