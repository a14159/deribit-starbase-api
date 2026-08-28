package io.contek.invoker.deribit.starbase.book;

/** Allocation-free visitor for one aggregated Price9 level. */
@FunctionalInterface
public interface PriceLevelConsumer {

  void onLevel(
      long instrumentId,
      int side,
      long priceMantissa,
      long quantityMantissa,
      int orderCount,
      long firstSortOrderId);
}
