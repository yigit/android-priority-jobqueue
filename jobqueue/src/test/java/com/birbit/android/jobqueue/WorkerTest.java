package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.ConsumerController.Worker;
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
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.test.timer.MockTimer;

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
public class WorkerTest {
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
        Worker worker = new ConsumerController.Worker(
                pmq, mq, factory, timer);
        worker.run();
        verify(mq).consume(any(MessageQueueConsumer.class));
    }

    private void setRunning(MessageQueue mq) {
        Reflection.field("running").ofType(AtomicBoolean.class).in(mq).get().set(true);
    }

    @Test
    public void idleMessage() {
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer);
        SafeMessageQueue mq = new SafeMessageQueue(timer);
        setRunning(pmq);
        setRunning(mq);
        timer.setNow(2001);
        Worker worker = new ConsumerController.Worker(pmq, mq, factory, timer);
        worker.queueConsumer.onIdle();
        Message message = pmq.next(dummyConsumer);
        assertThat(message, CoreMatchers.instanceOf(JobConsumerIdleMessage.class));
        assertThat(((JobConsumerIdleMessage) message).getLastJobCompleted(), CoreMatchers.is(2001L));
    }

    @Test
    public void runJobMessage() {
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer);
        setRunning(pmq);
        SafeMessageQueue mq = new SafeMessageQueue(timer);
        setRunning(mq);
        timer.setNow(2001);
        Worker worker = new ConsumerController.Worker(pmq, mq, factory, timer);
        RunJobMessage rjm = factory.obtain(RunJobMessage.class);
        JobHolder mockHolder = mock(JobHolder.class);
        when(mockHolder.safeRun(0)).thenReturn(JobHolder.RUN_RESULT_SUCCESS);
        rjm.setJobHolder(mockHolder);
        timer.setNow(3001);
        worker.queueConsumer.handleMessage(rjm);

        Message message = pmq.next(dummyConsumer);
        assertThat(message, CoreMatchers.instanceOf(RunJobResultMessage.class));
        RunJobResultMessage result = (RunJobResultMessage) message;
        assertThat(result.getResult(), CoreMatchers.is(JobHolder.RUN_RESULT_SUCCESS));
        assertThat(result.getJobHolder(), CoreMatchers.is(mockHolder));
        assertThat(worker.lastJobCompleted, CoreMatchers.is(3001L));
    }

    @Test
    public void removePokesAfterJobTest() {
        PriorityMessageQueue pmq = new PriorityMessageQueue(timer);
        setRunning(pmq);
        SafeMessageQueue mq = spy(new SafeMessageQueue(timer));
        setRunning(mq);
        timer.setNow(2001);
        Worker worker = new ConsumerController.Worker(pmq, mq, factory, timer);
        RunJobMessage rjm = factory.obtain(RunJobMessage.class);
        JobHolder mockHolder = mock(JobHolder.class);
        when(mockHolder.safeRun(0)).thenReturn(JobHolder.RUN_RESULT_SUCCESS);
        rjm.setJobHolder(mockHolder);
        timer.setNow(3001);
        verify(mq, times(0)).cancelMessages(Worker.pokeMessagePredicate);
        worker.queueConsumer.handleMessage(rjm);
        verify(mq, times(1)).cancelMessages(Worker.pokeMessagePredicate);
    }

    @Test
    public void pokePredicateTest() {
        CommandMessage cm = new CommandMessage();
        cm.set(CommandMessage.POKE);
        assertThat(Worker.pokeMessagePredicate.onMessage(cm), CoreMatchers.is(true));
        cm.set(CommandMessage.QUIT);
        assertThat(Worker.pokeMessagePredicate.onMessage(cm), CoreMatchers.is(false));
        assertThat(Worker.pokeMessagePredicate.onMessage(new RunJobMessage()),
                CoreMatchers.is(false));
    }
}
