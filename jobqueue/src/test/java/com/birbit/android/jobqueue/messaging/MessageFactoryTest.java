package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

@RunWith(JUnit4.class)
public class MessageFactoryTest {
    MessageFactory factory = new MessageFactory();
    @Test
    public void test() {
        AddJobMessage aj1 = factory.obtain(AddJobMessage.class);
        assertThat(aj1, isA(AddJobMessage.class));
        CommandMessage cm1 = factory.obtain(CommandMessage.class);
        assertThat(cm1, isA(CommandMessage.class));
        assertThat(factory.obtain(AddJobMessage.class), not(sameInstance(aj1)));
        assertThat(factory.obtain(CommandMessage.class), not(sameInstance(cm1)));
        factory.release(aj1);
        factory.release(cm1);
        assertThat(factory.obtain(AddJobMessage.class), sameInstance(aj1));
        assertThat(factory.obtain(CommandMessage.class), sameInstance(cm1));
        assertThat(factory.obtain(AddJobMessage.class), not(sameInstance(aj1)));
        assertThat(factory.obtain(CommandMessage.class), not(sameInstance(cm1)));
    }
}
