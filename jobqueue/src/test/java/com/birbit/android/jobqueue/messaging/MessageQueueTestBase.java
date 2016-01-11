package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.timer.MockTimer;
import com.path.android.jobqueue.timer.Timer;

import junit.framework.Assert;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.*;


import static org.hamcrest.MatcherAssert.assertThat;

abstract public class MessageQueueTestBase<T extends MessageQueue> {
    abstract T createMessageQueue(Timer timer);
    @Test
    public void postDelayed() throws InterruptedException {

        MockTimer timer = new MockTimer();
        final T mq = createMessageQueue(timer);
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
    public void addMessageOnIdle() throws InterruptedException {
        addMessageOnIdle(false);
    }

    @Test
    public void addDelayedMessageOnIdle() throws InterruptedException {
        addMessageOnIdle(true);
    }

    private void addMessageOnIdle(final boolean delayed) throws InterruptedException {
        final MockTimer timer = new MockTimer();
        final MessageQueue mq = createMessageQueue(timer);
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
        assertThat(thread.isAlive(), CoreMatchers.is(false));
    }

    @Test
    public void postAtNoIdleCall() throws InterruptedException {
        final MockTimer timer = new MockTimer();
        final MessageQueue mq = createMessageQueue(timer);
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
}
