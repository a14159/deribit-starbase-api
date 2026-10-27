package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.StarbaseException;

/** Structured REST/JSON-RPC failure. */
public final class StarbaseRestException extends StarbaseException {

  public static final int NO_ERROR_CODE = Integer.MIN_VALUE;
  public static final long NO_RETRY_AFTER = -1L;

  private final int httpStatus;
  private final int errorCode;
  private final String dataJson;
  private final boolean timeout;
  private final long retryAfterSeconds;

  StarbaseRestException(
      String message, int httpStatus, int errorCode, String dataJson, boolean timeout, Throwable cause) {
    this(message, httpStatus, errorCode, dataJson, timeout, NO_RETRY_AFTER, cause);
  }

  StarbaseRestException(
      String message,
      int httpStatus,
      int errorCode,
      String dataJson,
      boolean timeout,
      long retryAfterSeconds,
      Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
    this.dataJson = dataJson;
    this.timeout = timeout;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public int errorCode() {
    return errorCode;
  }

  public String dataJson() {
    return dataJson;
  }

  public boolean isTimeout() {
    return timeout;
  }

  /**
   * Returns the gateway's HTTP {@code Retry-After} delay in whole seconds, documented on rate-limited
   * (429) responses, or {@link #NO_RETRY_AFTER} when absent or not a non-negative delay-seconds value.
   */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
