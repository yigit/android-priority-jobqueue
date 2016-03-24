package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.testing.CleanupRule;
import com.birbit.android.jobqueue.testing.ThreadDumpRule;
import com.birbit.android.jobqueue.test.TestBase;
import com.birbit.android.jobqueue.test.timer.MockTimer;
import com.birbit.android.jobqueue.timer.Timer;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Factory;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.*;


import static org.hamcrest.MatcherAssert.assertThat;

abstract public class MessageQueueTestBase<T extends MessageQueue> {
    @Rule public Timeout timeout = Timeout.seconds(60);
    @Rule public ThreadDumpRule threadDump = new ThreadDumpRule();
    abstract T createMessageQueue(Timer timer, MessageFactory factory);
    @Test
    public void postDelayed() throws InterruptedException {

        MockTimer timer = new MockTimer();
        final T mq = createMessageQueue(timer, new MessageFactory());
        final CountDownLatch idleLatch = new CountDownLatch(1);
        final Throwable[] exception = new Throwable[1];
        final MessageQueueConsumer mqConsumer = new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {

            }

            @Override
            public void onIdle() {
                try {
                    assertThat(idleLatch.getCount(), CoreMatchers.is(1L));
                } catch (Throwable t) {
                    exception[0] = t;
                } finally {
                    idleLatch.countDown();
                }

            }
        };

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                mq.consume(mqConsumer);
            }
        });
        thread.start();
        assertThat(idleLatch.await(10, TimeUnit.SECONDS), CoreMatchers.is(true));
        timer.incrementMs(1000000);
        Thread.sleep(1000);
        mq.stop();
        thread.join(5000);
        assertThat(exception[0], CoreMatchers.nullValue());
        assertThat(thread.isAlive(), CoreMatchers.is(false));
    }

    @Test
    public void recycleOnClear() {
        MessageFactory factory = spy(new MessageFactory());
        MockTimer mockTimer = new MockTimer();
        T mq = createMessageQueue(mockTimer, factory);
        CommandMessage cm = factory.obtain(CommandMessage.class);
        cm.set(CommandMessage.POKE);
        mq.post(cm);
        mq.clear();
        verify(factory).release(cm);
    }

    @Test
    public void recycleOnConsume() {
        MessageFactory factory = spy(new MessageFactory());
        MockTimer mockTimer = new MockTimer();
        final T mq = createMessageQueue(mockTimer, factory);
        CommandMessage cm = factory.obtain(CommandMessage.class);
        cm.set(CommandMessage.POKE);
        mq.post(cm);
        mq.consume(new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                mq.stop();
            }

            @Override
            public void onIdle() {

            }
        });
        verify(factory).release(cm);
    }

    @Test
    public void recycleOnCancel() {
        MessageFactory factory = spy(new MessageFactory());
        MockTimer mockTimer = new MockTimer();
        final T mq = createMessageQueue(mockTimer, factory);
        final CommandMessage cm = factory.obtain(CommandMessage.class);
        cm.set(CommandMessage.POKE);
        mq.post(cm);

        final CommandMessage cm2 = factory.obtain(CommandMessage.class);
        cm2.set(CommandMessage.POKE);
        mq.post(cm2);
        mq.cancelMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return message == cm;
            }
        });
        verify(factory).release(cm);
        verify(factory, times(0)).release(cm2);
    }

    @Test
    public void addMessageOnIdle() throws InterruptedException {
        addMessageOnIdle(false);
    }

    @Test
    public void addDelayedMessageOnIdle() throws InterruptedException {
        addMessageOnIdle(true);
    }

    private void addMessageOnIdle(final boolean delayed) throws InterruptedException {
        final MockTimer timer = new MockTimer();
        final MessageQueue mq = createMessageQueue(timer, new MessageFactory());
        final CountDownLatch idleLatch = new CountDownLatch(1);
        final CountDownLatch runLatch = new CountDownLatch(1);
        final MessageQueueConsumer mqConsumer = new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                if (message.type == Type.COMMAND && ((CommandMessage) message).getWhat() == CommandMessage.POKE) {
                    runLatch.countDown();
                }
            }

            @Override
            public void onIdle() {
                if (idleLatch.getCount() == 1) {
                    CommandMessage cm = new CommandMessage();
                    cm.set(CommandMessage.POKE);
                    if (delayed) {
                        mq.postAt(cm, timer.nanoTime() + 100000);
                    } else {
                        mq.post(cm);
                    }

                    idleLatch.countDown();
                }
            }
        };

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                mq.consume(mqConsumer);
            }
        });
        thread.start();
        assertThat(idleLatch.await(10, TimeUnit.SECONDS), CoreMatchers.is(true));
        timer.incrementMs(1000000);
        assertThat(runLatch.await(10, TimeUnit.SECONDS), CoreMatchers.is(true));
        mq.stop();
        thread.join(5000);
        if (thread.isAlive()) {
            threadDump.failed(new AssertionFailedError("thread did not die"), null);
        }
        assertThat(thread.isAlive(), CoreMatchers.is(false));
    }

    @Test
    public void postAtNoIdleCall() throws InterruptedException {
        final MockTimer timer = new MockTimer();
        final MessageQueue mq = createMessageQueue(timer, new MessageFactory());
        final CountDownLatch idleLatch = new CountDownLatch(1);
        final CountDownLatch firstIdleLatch = new CountDownLatch(1);
        final CountDownLatch runLatch = new CountDownLatch(1);
        final MessageQueueConsumer mqConsumer = new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                if (message.type == Type.COMMAND &&
                        ((CommandMessage) message).getWhat() == CommandMessage.POKE) {
                    runLatch.countDown();
                }
            }

            @Override
            public void onIdle() {
                if (firstIdleLatch.getCount() == 1) {
                    firstIdleLatch.countDown();
                } else {
                    idleLatch.countDown();
                }
            }
        };

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                mq.consume(mqConsumer);
            }
        });
        thread.start();
        firstIdleLatch.await();
        CommandMessage cm = new CommandMessage();
        cm.set(CommandMessage.POKE);
        mq.postAt(cm, 100);
        assertThat(idleLatch.await(3, TimeUnit.SECONDS), CoreMatchers.is(false));
        timer.incrementNs(100);
        assertThat(idleLatch.await(3, TimeUnit.SECONDS), CoreMatchers.is(true));
        mq.stop();
        thread.join();
    }

    @Test
    public void postWhileIdle() throws InterruptedException {
        final MockTimer timer = new MockTimer();
        final MessageQueue mq = createMessageQueue(timer, new MessageFactory());
        final CountDownLatch idleEnterLatch = new CountDownLatch(1);
        final CountDownLatch idleExitLatch = new CountDownLatch(1);
        final CountDownLatch handleMessage = new CountDownLatch(1);
        final CommandMessage cm = new CommandMessage();
        cm.set(CommandMessage.POKE);
        final MessageQueueConsumer consumer = new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                if (message == cm) {
                    handleMessage.countDown();
                }
            }

            @Override
            public void onIdle() {
                idleEnterLatch.countDown();
                try {
                    idleExitLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                mq.consume(consumer);
            }
        });
        thread.start();
        MatcherAssert.assertThat(idleEnterLatch.await(30, TimeUnit.SECONDS), CoreMatchers.is(true));
        mq.post(cm);
        idleExitLatch.countDown();
        MatcherAssert.assertThat(handleMessage.await(30, TimeUnit.SECONDS), CoreMatchers.is(true));
        mq.stop();
        thread.join(5000);
        assertThat(thread.isAlive(), CoreMatchers.is(false));
    }
}
