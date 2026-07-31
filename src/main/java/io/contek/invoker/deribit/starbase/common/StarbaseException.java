package io.contek.invoker.deribit.starbase.common;

public class StarbaseException extends RuntimeException {

  public StarbaseException(String message) {
    super(message);
  }

  public StarbaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
