package com.ktpm.common;

public class BusinessException extends RuntimeException {
  public enum Status {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409);
    private final int code;

    Status(int code) {
      this.code = code;
    }

    public int value() {
      return code;
    }
  }

  public final Status status;

  public BusinessException(Status status, String message) {
    super(message);
    this.status = status;
  }

  public static BusinessException conflict(String message) {
    return new BusinessException(Status.CONFLICT, message);
  }

  public static BusinessException missing(String message) {
    return new BusinessException(Status.NOT_FOUND, message);
  }

  public static BusinessException forbidden(String message) {
    return new BusinessException(Status.FORBIDDEN, message);
  }

  public static BusinessException bad(String message) {
    return new BusinessException(Status.BAD_REQUEST, message);
  }

  public static BusinessException unauthorized(String message) {
    return new BusinessException(Status.UNAUTHORIZED, message);
  }
}
