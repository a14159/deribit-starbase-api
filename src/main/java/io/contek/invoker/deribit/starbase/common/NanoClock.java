package io.contek.invoker.deribit.starbase.common;

@FunctionalInterface
public interface NanoClock {

  NanoClock SYSTEM = System::nanoTime;

  long nanoTime();
}
