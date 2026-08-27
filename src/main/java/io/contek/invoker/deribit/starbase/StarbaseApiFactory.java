package io.contek.invoker.deribit.starbase;

import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataApi;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataContext;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryApi;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestApi;
import io.contek.invoker.deribit.starbase.rest.OpenOrderRecoveryCache;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestCredentials;

/** Constructs independent API objects from caller-supplied contexts. */
public final class StarbaseApiFactory {

  public StarbaseMarketDataApi marketData(StarbaseMarketDataContext context) {
    return new StarbaseMarketDataApi(context);
  }

  /** Constructs one public market-data API owning the required redundant A/B contexts. */
  public StarbaseMarketDataApi marketData(
      StarbaseMarketDataContext first, StarbaseMarketDataContext second) {
    return new StarbaseMarketDataApi(first, second);
  }

  public StarbaseOrderEntryApi orderEntry(
      StarbaseOrderEntryContext context, StarbaseCredentials credentials) {
    return new StarbaseOrderEntryApi(context, credentials);
  }

  /** Constructs one public order-entry API owning separate A/B gateway sessions. */
  public StarbaseOrderEntryApi orderEntry(
      StarbaseOrderEntryContext firstContext,
      StarbaseCredentials firstCredentials,
      StarbaseOrderEntryContext secondContext,
      StarbaseCredentials secondCredentials,
      OpenOrderRecoveryCache recoveryCache) {
    return new StarbaseOrderEntryApi(
        firstContext,
        firstCredentials,
        secondContext,
        secondCredentials,
        recoveryCache);
  }

  public StarbaseRestApi rest(StarbaseRestContext context, StarbaseRestCredentials credentials) {
    return new StarbaseRestApi(context, credentials);
  }
}
