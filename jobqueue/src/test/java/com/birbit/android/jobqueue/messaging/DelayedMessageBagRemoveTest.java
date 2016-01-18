package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnit4.class)
public class DelayedMessageBagRemoveTest {
    MessageFactory factory = new MessageFactory();
    DelayedMessageBag bag = new DelayedMessageBag(factory);
    List<Long> items = Arrays.asList(1000L, 2000L, 3000L);
    Map<Long, Message> added = new HashMap<>();

    @Test
    public void testRemoveAll() {
        for (Long readyNs : items) {
            add(readyNs);
        }
        bag.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return true;
            }
        });
        MessageQueue mq = mock(MessageQueue.class);
        Long t1 = bag.flushReadyMessages(500, mq);
        assertThat(t1, CoreMatchers.nullValue());
        bag.flushReadyMessages(4000, mq);
        verify(mq, times(0)).post(any(Message.class));
    }

    @Test
    public void testRemoveNothing() {
        for (Long readyNs : items) {
            add(readyNs);
        }
        bag.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return false;
            }
        });
        MessageQueue mq = mock(MessageQueue.class);
        long t1 = bag.flushReadyMessages(500, mq);
        assertThat(t1, CoreMatchers.is(1000L));
        bag.flushReadyMessages(4000, mq);
        verify(mq, times(3)).post(any(Message.class));
    }

    @Test
    public void testRemoveUnmatch() {
        testRemove(0, 1000, 2000, 3000);
    }

    @Test
    public void testRemove1000() {
        testRemove(1000, 2000, 3000);
    }

    @Test
    public void testRemove2000() {
        testRemove(2000, 1000, 3000);
    }

    @Test
    public void testRemove3000() {
        testRemove(3000, 1000, 2000);
    }

    public void testRemove(final long itemToRemove, long... toBeReceived) {
        for (Long readyNs : items) {
            add(readyNs);
        }
        bag.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return message.readyNs == itemToRemove;
            }
        });
        MessageQueue mq = mock(MessageQueue.class);
        bag.flushReadyMessages(4000, mq);
        for (long item : toBeReceived) {
            verify(mq).post(same(added.get(item)));
        }
    }

    private Message add(long readyNs) {
        CommandMessage msg = factory.obtain(CommandMessage.class);
        bag.add(msg, readyNs);
        added.put(readyNs, msg);
        return msg;
    }
}
