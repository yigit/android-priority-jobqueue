package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.ConsumerManager.Consumer;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.test.timer.MockTimer;

import org.fest.reflect.core.Reflection;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class ConsumerTest {
    MessageFactory factory = new MessageFactory();
    MockTimer timer = new MockTimer();

    private MessageQueueConsumer dummyConsumer = new MessageQueueConsumer() {
        @Override
        public void handleMessage(Message message) {

        }

        @Override
        public void onIdle() {

        }
    };

    @Test
    public void init() {
        PriorityMessageQueue pmq = mock(PriorityMessageQueue.class);
        SafeMessageQueue mq = mock(SafeMessageQueue.class);
        Consumer consumer = new Consumer(
                pmq, mq, factory, timer);
        consumer.run();
        verify(mq).consume(any(MessageQueueConsumer.class));
    }

    private void setRunning(MessageQueue mq) {
        Reflection.field("running").ofType(AtomicBoolean.class).in(mq).get().set(true);
    }

    @Test
    public void idleMessage() {
        MessageFactory factory = new MessageFactory();
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer, factory);
        SafeMessageQueue mq = new SafeMessageQueue(timer, factory, "test");
        setRunning(pmq);
        setRunning(mq);
        timer.setNow(2001);
        Consumer consumer = new Consumer(pmq, mq, factory, timer);
        consumer.queueConsumer.onIdle();
        Message message = pmq.next(dummyConsumer);
        assertThat(message, CoreMatchers.instanceOf(JobConsumerIdleMessage.class));
        assertThat(((JobConsumerIdleMessage) message).getLastJobCompleted(), CoreMatchers.is(2001L));
    }

    @Test
    public void runJobMessage() {
        MessageFactory factory = new MessageFactory();
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer, factory);
        setRunning(pmq);
        SafeMessageQueue mq = new SafeMessageQueue(timer, factory, "test");
        setRunning(mq);
        timer.setNow(2001);
        Consumer consumer = new Consumer(pmq, mq, factory, timer);
        RunJobMessage rjm = factory.obtain(RunJobMessage.class);
        JobHolder mockHolder = mock(JobHolder.class);
        when(mockHolder.safeRun(0)).thenReturn(JobHolder.RUN_RESULT_SUCCESS);
        rjm.setJobHolder(mockHolder);
        timer.setNow(3001);
        consumer.queueConsumer.handleMessage(rjm);

        Message message = pmq.next(dummyConsumer);
        assertThat(message, CoreMatchers.instanceOf(RunJobResultMessage.class));
        RunJobResultMessage result = (RunJobResultMessage) message;
        assertThat(result.getResult(), CoreMatchers.is(JobHolder.RUN_RESULT_SUCCESS));
        assertThat(result.getJobHolder(), CoreMatchers.is(mockHolder));
        assertThat(consumer.lastJobCompleted, CoreMatchers.is(3001L));
    }

    @Test
    public void removePokesAfterJobTest() {
        MessageFactory factory = new MessageFactory();
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer, factory);
        setRunning(pmq);
        SafeMessageQueue mq = spy(new SafeMessageQueue(timer, factory, "test"));
        setRunning(mq);
        timer.setNow(2001);
        Consumer consumer = new Consumer(pmq, mq, factory, timer);
        RunJobMessage rjm = factory.obtain(RunJobMessage.class);
        JobHolder mockHolder = mock(JobHolder.class);
        when(mockHolder.safeRun(0)).thenReturn(JobHolder.RUN_RESULT_SUCCESS);
        rjm.setJobHolder(mockHolder);
        timer.setNow(3001);
        verify(mq, times(0)).cancelMessages(Consumer.pokeMessagePredicate);
        consumer.queueConsumer.handleMessage(rjm);
        verify(mq, times(1)).cancelMessages(Consumer.pokeMessagePredicate);
    }

    @Test
    public void pokePredicateTest() {
        CommandMessage cm = new CommandMessage();
        cm.set(CommandMessage.POKE);
        assertThat(Consumer.pokeMessagePredicate.onMessage(cm), CoreMatchers.is(true));
        cm.set(CommandMessage.QUIT);
        assertThat(Consumer.pokeMessagePredicate.onMessage(cm), CoreMatchers.is(false));
        assertThat(Consumer.pokeMessagePredicate.onMessage(new RunJobMessage()),
                CoreMatchers.is(false));
    }
}
