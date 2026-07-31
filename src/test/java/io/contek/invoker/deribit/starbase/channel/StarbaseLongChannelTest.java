package io.contek.invoker.deribit.starbase.channel;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;


public final class StarbaseLongChannelTest {

  public void testListenersRegisterDispatchInOrderAndCanBeRemovedIdempotently() {
    StarbaseLongChannel channel = new StarbaseLongChannel();
    long[] observed = new long[4];
    int[] count = new int[1];
    var first =
        channel.addListener(
            (key, value, timestamp) -> observed[count[0]++] = key + value + timestamp);
    channel.addListener(
        (key, value, timestamp) -> observed[count[0]++] = key * value + timestamp);

    channel.publish(2L, 3L, 5L);
    first.close();
    first.close();
    channel.publish(3L, 4L, 6L);

    assertEquals(3, count[0]);
    assertEquals(10L, observed[0]);
    assertEquals(11L, observed[1]);
    assertEquals(18L, observed[2]);
  }
}
