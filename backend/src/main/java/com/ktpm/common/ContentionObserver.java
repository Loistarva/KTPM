package com.ktpm.common;

/** Framework-free observability port for the bid retry path. */
public interface ContentionObserver {
  void bidRetry();
}
