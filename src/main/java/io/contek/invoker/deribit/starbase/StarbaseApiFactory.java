package io.contek.invoker.deribit.starbase;

import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataApi;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataContext;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryApi;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestApi;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestCredentials;

/** Constructs independent API objects from caller-supplied contexts. */
public final class StarbaseApiFactory {

  public StarbaseMarketDataApi marketData(StarbaseMarketDataContext context) {
    return new StarbaseMarketDataApi(context);
  }

  public StarbaseOrderEntryApi orderEntry(
      StarbaseOrderEntryContext context, StarbaseCredentials credentials) {
    return new StarbaseOrderEntryApi(context, credentials);
  }

  public StarbaseRestApi rest(StarbaseRestContext context, StarbaseRestCredentials credentials) {
    return new StarbaseRestApi(context, credentials);
  }
}
