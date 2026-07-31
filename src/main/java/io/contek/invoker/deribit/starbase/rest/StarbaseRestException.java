package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.StarbaseException;

/** Structured REST/JSON-RPC failure. */
public final class StarbaseRestException extends StarbaseException {

  public static final int NO_ERROR_CODE = Integer.MIN_VALUE;

  private final int httpStatus;
  private final int errorCode;
  private final String dataJson;
  private final boolean timeout;

  StarbaseRestException(
      String message, int httpStatus, int errorCode, String dataJson, boolean timeout, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
    this.dataJson = dataJson;
    this.timeout = timeout;
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
}
