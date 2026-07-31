package io.contek.invoker.deribit.starbase.book;

/** Allocation-free notification emitted after a coherent book publication boundary. */
@FunctionalInterface
public interface BookPublicationListener {

  void onPublication(long version, long sequenceNumber, long transactTimeNanos);
}
