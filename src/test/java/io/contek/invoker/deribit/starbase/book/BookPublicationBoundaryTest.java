package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

public final class BookPublicationBoundaryTest {

  public void testOpenTransactionSuppressesIntermediatePublication() {
    RecordingListener listener = new RecordingListener();
    BookPublicationBoundary boundary = new BookPublicationBoundary(listener);

    boundary.onMutation();
    boundary.onMessageBoundary(
        MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION, 10L, 100L, false);
    boundary.onMutation();
    boundary.onMessageBoundary(0, 11L, 101L, false);

    assertEquals(0, listener.count);
    assertTrue(boundary.isTransactionOpen());

    boundary.onMessageBoundary(
        MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION, 12L, 102L, false);

    assertEquals(1, listener.count);
    assertEquals(12L, listener.sequenceNumber);
    assertEquals(102L, listener.transactTimeNanos);
    assertFalse(boundary.isTransactionOpen());
  }

  public void testSingleMessageTransactionPublishesOneCoherentVersion() {
    RecordingListener listener = new RecordingListener();
    BookPublicationBoundary boundary = new BookPublicationBoundary(listener);
    boundary.onMutation();

    boundary.onMessageBoundary(
        MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
            | MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION,
        20L,
        200L,
        false);

    assertEquals(1, listener.count);
    assertEquals(1L, listener.version);
    assertEquals(1L, boundary.publishedVersion());
  }

  public void testEndOfCyclePublishesPendingNonTransactionalMutationsOnlyOnce() {
    RecordingListener listener = new RecordingListener();
    BookPublicationBoundary boundary = new BookPublicationBoundary(listener);
    boundary.onMutation();
    boundary.onMessageBoundary(0, 30L, 300L, false);

    boundary.onMessageBoundary(0, 31L, 301L, true);
    boundary.onMessageBoundary(0, 32L, 302L, true);

    assertEquals(1, listener.count);
    assertEquals(31L, listener.sequenceNumber);
  }

  public void testInvalidLifecycleFailsClosedWithoutPublishing() {
    RecordingListener listener = new RecordingListener();
    BookPublicationBoundary unmatchedEnd = new BookPublicationBoundary(listener);

    assertThrows(
        StarbaseProtocolException.class,
        () ->
            unmatchedEnd.onMessageBoundary(
                MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION, 1L, 1L, false));
    assertTrue(unmatchedEnd.isFailed());

    BookPublicationBoundary nestedStart = new BookPublicationBoundary(listener);
    nestedStart.onMessageBoundary(
        MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION, 2L, 2L, false);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            nestedStart.onMessageBoundary(
                MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION, 3L, 3L, false));
    assertTrue(nestedStart.isFailed());

    BookPublicationBoundary cycleInsideTransaction = new BookPublicationBoundary(listener);
    cycleInsideTransaction.onMessageBoundary(
        MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION, 3L, 3L, false);
    assertThrows(
        StarbaseProtocolException.class,
        () -> cycleInsideTransaction.onMessageBoundary(0, 4L, 4L, true));

    assertEquals(0, listener.count);
    assertTrue(cycleInsideTransaction.isFailed());
  }

  private static final class RecordingListener implements BookPublicationListener {
    int count;
    long version;
    long sequenceNumber;
    long transactTimeNanos;

    @Override
    public void onPublication(long version, long sequenceNumber, long transactTimeNanos) {
      count++;
      this.version = version;
      this.sequenceNumber = sequenceNumber;
      this.transactTimeNanos = transactTimeNanos;
    }
  }
}
