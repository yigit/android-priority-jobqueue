package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.test.timer.MockTimer;
import com.birbit.android.jobqueue.timer.Timer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

@RunWith(JUnit4.class)
public class PriorityMessageQueueTest extends MessageQueueTestBase<PriorityMessageQueue> {
    PriorityMessageQueue mq = new PriorityMessageQueue(new MockTimer(), new MessageFactory());

    @Test
    public void test1() {
        CommandMessage mC1 = new CommandMessage();
        CommandMessage mC2 = new CommandMessage();
        AddJobMessage aj1 = new AddJobMessage();
        AddJobMessage aj2 = new AddJobMessage();
        mq.post(mC1);
        mq.post(mC2);
        mq.post(aj1);
        mq.post(aj2);
        final List<Message> expectedOrder = Arrays.asList(aj1, aj2, mC1, mC2);
        mq.consume(new MessageQueueConsumer() {
            int index;
            @Override
            public void handleMessage(Message message) {
                assertThat(message, is (expectedOrder.get(index++)));
                if (index == expectedOrder.size()) {
                    mq.stop();
                }
            }

            @Override
            public void onIdle() {

            }
        });
    }

    @Override
    PriorityMessageQueue createMessageQueue(Timer timer, MessageFactory factory) {
        return new PriorityMessageQueue(timer, factory);
    }
}
