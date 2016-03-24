package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.mockito.Mockito.*;
@RunWith(JUnit4.class)
public class DelayedMessageBagRecycleTest {
    MessageFactory factory = spy(new MessageFactory());
    DelayedMessageBag bag = new DelayedMessageBag(factory);

    @Test
    public void recycleOnClear() {
        CommandMessage cm = factory.obtain(CommandMessage.class);
        cm.set(CommandMessage.POKE);
        bag.add(cm, 1000);
        bag.clear();
        verify(factory).release(cm);
    }

    @Test
    public void recycleOnCancel() {
        final CommandMessage cm = factory.obtain(CommandMessage.class);
        cm.set(CommandMessage.POKE);
        bag.add(cm, 1000);

        final CommandMessage cm2 = factory.obtain(CommandMessage.class);
        cm2.set(CommandMessage.POKE);
        bag.add(cm2, 1000);
        bag.removeMessages(new MessagePredicate() {
            @Override
            public boolean onMessage(Message message) {
                return message == cm;
            }
        });
        verify(factory).release(cm);
        verify(factory, times(0)).release(cm2);
    }
}
