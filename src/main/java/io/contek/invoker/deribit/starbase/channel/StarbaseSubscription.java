package io.contek.invoker.deribit.starbase.channel;

public interface StarbaseSubscription extends AutoCloseable {

  @Override
  void close();
}
