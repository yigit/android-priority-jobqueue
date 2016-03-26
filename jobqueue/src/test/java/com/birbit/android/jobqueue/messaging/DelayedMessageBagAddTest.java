package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(Parameterized.class)
public class DelayedMessageBagAddTest {
    MessageFactory factory = new MessageFactory();
    DelayedMessageBag bag = new DelayedMessageBag(factory);

    List<Long> ordered;
    Map<Long, Message> added = new HashMap<>();

    public DelayedMessageBagAddTest(List<Long> ordered) {
        this.ordered = ordered;
    }

    @Parameterized.Parameters(name = "{0}")
    static public List<List<Long>> params() {
        List<List<Long>> result = new ArrayList<>();
        for (int a = 0; a < 3; a++) {

            for (int b = 0; b < 3; b ++) {
                if (b == a) {
                    continue;
                }
                for (int c = 0; c < 3; c ++) {
                    if (c == a || c == b) {
                        continue;
                    }
                    Long[] items = new Long[3];
                    items[a] = 1000L;
                    items[b] = 2000L;
                    items[c] = 3000L;
                    result.add(Arrays.asList(items));
                }
            }
        }
        return result;
    }

    @Test
    public void testAddOrdered() {
        for (Long readyNs : ordered) {
            add(readyNs);
        }
        Message m1 = added.get(1000L);
        Message m2 = added.get(2000L);
        Message m3 = added.get(3000L);
        assertThat(m1, CoreMatchers.notNullValue());
        assertThat(m2, CoreMatchers.notNullValue());
        assertThat(m3, CoreMatchers.notNullValue());
        MessageQueue mq = mock(MessageQueue.class);
        long t1 = bag.flushReadyMessages(500, mq);
        assertThat(t1, CoreMatchers.is(1000L));
        verify(mq, times(0)).post(any(Message.class));
        long t2 = bag.flushReadyMessages(1000, mq);
        assertThat(t2, CoreMatchers.is(2000L));
        verify(mq).post(m1);
        Long t3 = bag.flushReadyMessages(3001, mq);
        assertThat(t3, CoreMatchers.nullValue());
        verify(mq).post(m2);
        verify(mq).post(m3);
    }

    private Message add(long readyNs) {
        CommandMessage msg = factory.obtain(CommandMessage.class);
        bag.add(msg, readyNs);
        added.put(readyNs, msg);
        return msg;
    }
}
