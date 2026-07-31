package io.contek.invoker.deribit.starbase.orderentry;

import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.common.AbstractStarbaseApi;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import java.util.Objects;

/**
 * Public order-entry facade placeholder.
 *
 * <p>The tested connection, authentication, command, state, and recovery components are
 * not yet composed behind this class; see {@code docs/implementation-status.md}.
 */
public final class StarbaseOrderEntryApi extends AbstractStarbaseApi {

  private final StarbaseOrderEntryContext context;
  private final StarbaseCredentials credentials;
  private final StarbaseLongChannel orderEvents = new StarbaseLongChannel();
  private final StarbaseLongChannel fills = new StarbaseLongChannel();
  private final StarbaseLongChannel sessionEvents = new StarbaseLongChannel();

  public StarbaseOrderEntryApi(
      StarbaseOrderEntryContext context, StarbaseCredentials sourceCredentials) {
    this.context = Objects.requireNonNull(context, "context");
    Objects.requireNonNull(sourceCredentials, "sourceCredentials");
    this.credentials =
        new StarbaseCredentials(
            sourceCredentials.copyUsername(), sourceCredentials.copyPassword());
  }

  public StarbaseOrderEntryContext context() {
    return context;
  }

  public boolean isAuthenticated() {
    return false;
  }

  public StarbaseLongChannel getOrderEventsChannel() {
    return orderEvents;
  }

  public StarbaseLongChannel getFillsChannel() {
    return fills;
  }

  public StarbaseLongChannel getSessionEventsChannel() {
    return sessionEvents;
  }

  @Override
  protected void onClose() {
    credentials.close();
  }
}
