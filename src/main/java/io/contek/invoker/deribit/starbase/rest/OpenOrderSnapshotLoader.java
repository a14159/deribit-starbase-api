package io.contek.invoker.deribit.starbase.rest;

import java.util.List;

@FunctionalInterface
public interface OpenOrderSnapshotLoader {
  List<StarbaseOpenOrder> load();
}
