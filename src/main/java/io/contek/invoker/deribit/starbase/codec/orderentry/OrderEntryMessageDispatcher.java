package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Validates and routes the hardcoded order-entry schema-v15 message subset. */
public final class OrderEntryMessageDispatcher {

  public static void dispatch(ByteBuffer buffer, int offset, OrderEntryMessageHandler handler) {
    if (handler == null) {
      throw new NullPointerException("handler");
    }
    int templateId = OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    switch (templateId) {
      case 1 -> LogonDecoder.validate(buffer, offset);
      case 2 -> LogonConfirmationCodec.validate(buffer, offset);
      case 4 -> LogoutCodec.validate(buffer, offset);
      case 5 -> LoggedOutCodec.validate(buffer, offset);
      case 10 -> HeartbeatCodec.validate(buffer, offset);
      case 11 -> TestRequestCodec.validate(buffer, offset);
      case 20 -> ResendRequestCodec.validate(buffer, offset);
      case 21 -> GapFillDecoder.validate(buffer, offset);
      case 30 -> SessionRejectDecoder.validate(buffer, offset);
      case 100 -> NewOrderRequestDecoder.validate(buffer, offset);
      case 110 -> AmendOrderRequestDecoder.validate(buffer, offset);
      case 120 -> CancelOrderRequestDecoder.validate(buffer, offset);
      case 125 -> CancelOrderByIdRequestDecoder.validate(buffer, offset);
      case 140 -> MassCancelRequestDecoder.validate(buffer, offset);
      case 200 -> NewOrderResponseDecoder.validate(buffer, offset);
      case 202 -> NewOrderRejectDecoder.validate(buffer, offset);
      case 210 -> AmendOrderResponseDecoder.validate(buffer, offset);
      case 212 -> AmendOrderRejectDecoder.validate(buffer, offset);
      case 220 -> CancelOrderResponseDecoder.validate(buffer, offset);
      case 222 -> CancelOrderRejectDecoder.validate(buffer, offset);
      case 240 -> MassCancelResponseDecoder.validate(buffer, offset);
      case 242 -> MassCancelRejectDecoder.validate(buffer, offset);
      case 300 -> OrderFilledDecoder.validate(buffer, offset);
      case 310 -> OrdersCanceledDecoder.validate(buffer, offset);
      case 312 -> OrderPlacedDecoder.validate(buffer, offset);
      default ->
          throw new StarbaseProtocolException(
              "unsupported order-entry templateId: " + templateId);
    }
    handler.onMessage(templateId, buffer, offset);
  }

  private OrderEntryMessageDispatcher() {}
}
