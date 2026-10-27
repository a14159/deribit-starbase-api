package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.channel.StarbaseBlockTradeChannel;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.BlockTradeDecoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/**
 * Fail-closed allocation-free framing for per-instrument block-trade transactions delimited by the
 * message-header transaction flags.
 *
 * <p>Live publication can resume mid-transaction after startup or a fresh-snapshot recovery, so a
 * reset context first joins: fills of a transaction whose start was not observed are skipped until
 * that transaction ends or a new one starts. Once aligned, an orphan, nested, or cross-instrument
 * fill fails closed.
 */
final class BlockTradeContext {

  private long instrumentId;
  private int fillIndex;
  private boolean open;
  private boolean joining = true;

  void onBlockTrade(
      ByteBuffer buffer,
      int offset,
      long sequenceNumber,
      long timestampNanos,
      BlockTradeChannelRouter channels) {
    boolean start = MarketDataMessageHeaderCodec.isStartOfTransaction(buffer, offset);
    boolean end = MarketDataMessageHeaderCodec.isEndOfTransaction(buffer, offset);
    long tradeInstrumentId = BlockTradeDecoder.instrumentId(buffer, offset);
    if (joining) {
      if (!start) {
        joining = !end;
        return;
      }
      joining = false;
    }
    if (start) {
      if (open) {
        fail("nested BlockTrade transaction");
      }
      instrumentId = tradeInstrumentId;
      fillIndex = 0;
      open = true;
    } else if (!open) {
      fail("BlockTrade without startOfTransaction");
    } else if (tradeInstrumentId != instrumentId) {
      fail("BlockTrade instrument changed inside its transaction");
    }
    StarbaseBlockTradeChannel channel = channels.existing(instrumentId);
    if (channel != null) {
      channel.publish(
          BlockTradeDecoder.matchId(buffer, offset),
          instrumentId,
          BlockTradeDecoder.blockTradeId(buffer, offset),
          BlockTradeDecoder.blockRfqId(buffer, offset),
          BlockTradeDecoder.fillQuantityMantissa(buffer, offset),
          BlockTradeDecoder.fillPriceMantissa(buffer, offset),
          BlockTradeDecoder.markPriceMantissa(buffer, offset),
          BlockTradeDecoder.indexPriceMantissa(buffer, offset),
          BlockTradeDecoder.impliedVolatility(buffer, offset),
          BlockTradeDecoder.takerFlags(buffer, offset),
          BlockTradeDecoder.numberOfLegs(buffer, offset),
          fillIndex,
          end,
          sequenceNumber,
          timestampNanos);
    }
    fillIndex++;
    if (end) {
      open = false;
    }
  }

  /** Forgets any open transaction and rejoins at the next observed transaction boundary. */
  void reset() {
    open = false;
    joining = true;
    instrumentId = 0;
    fillIndex = 0;
  }

  private void fail(String message) {
    open = false;
    throw new StarbaseProtocolException(message);
  }
}
