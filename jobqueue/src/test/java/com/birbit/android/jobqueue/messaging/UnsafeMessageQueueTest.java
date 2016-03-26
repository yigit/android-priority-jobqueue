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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnit4.class)
public class UnsafeMessageQueueTest {
    UnsafeMessageQueue mq = new UnsafeMessageQueue(new MessageFactory(), "test");
    List<Long> items = Arrays.asList(1000L, 2000L, 3000L);
    Map<Long, Message> added = new HashMap<>();
    
    @Test
    public void simplePost() {
        Message m = new CommandMessage();
        Message m2 = new CommandMessage();
        Message m3 = new CommandMessage();
        mq.post(m);
        mq.post(m2);
        mq.post(m3);
        assertThat(mq.next(), is(m));
        assertThat(mq.next(), is(m2));
        assertThat(mq.next(), is(m3));
    }

    @Test
    public void simplePostAtFront() {
        Message m = new CommandMessage();
        Message m2 = new CommandMessage();
        Message m3 = new CommandMessage();
        mq.postAtFront(m);
        mq.postAtFront(m2);
        mq.postAtFront(m3);
        assertThat(mq.next(), is(m3));
        assertThat(mq.next(), is(m2));
        assertThat(mq.next(), is(m));
    }

    @Test
    public void testRemoveAll() {
        for (Long readyNs : items) {
            add(readyNs);
        }
        mq.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return true;
            }
        });
        assertThat(mq.next(), nullValue());
    }

    @Test
    public void testRemoveNothing() {
        for (Long readyNs : items) {
            add(readyNs);
        }
        mq.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return false;
            }
        });
        assertThat(mq.next(), sameInstance(added.get(1000L)));
        assertThat(mq.next(), sameInstance(added.get(2000L)));
        assertThat(mq.next(), sameInstance(added.get(3000L)));
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
        for (Long id : items) {
            add(id);
        }
        mq.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return message == added.get(itemToRemove);
            }
        });
        for (long item : toBeReceived) {
            assertThat(mq.next(), is(added.get(item)));
        }
        assertThat(mq.next(), nullValue());
    }

    private Message add(long id) {
        CommandMessage msg = new CommandMessage();
        mq.post(msg);
        added.put(id, msg);
        return msg;
    }
}
