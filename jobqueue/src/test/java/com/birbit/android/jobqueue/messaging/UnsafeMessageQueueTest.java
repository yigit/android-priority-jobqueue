package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

@RunWith(JUnit4.class)
public class UnsafeMessageQueueTest {
    UnsafeMessageQueue mq = new UnsafeMessageQueue();

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
}
