package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.log.JqLog;

import java.util.Arrays;

public class MessageFactory {
    private static final int CACHE_LIMIT = 20;
    Message[] pools = new Message[Type.values().length];
    int[] counts = new int[pools.length];

    public MessageFactory() {
        Arrays.fill(counts, 0);
    }

    public <T extends Message> T obtain(Class<T> klass) {
        final Type type = Type.mapping.get(klass);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (type) {
            Message message = pools[type.ordinal()];
            if (message != null) {
                pools[type.ordinal()] = message.next;
                counts[type.ordinal()] -= 1;
                message.next = null;
                //noinspection unchecked
                return (T) message;
            }
            try {
                return klass.newInstance();
            } catch (InstantiationException e) {
                JqLog.e(e, "Cannot create an instance of " + klass + ". Make sure it has a empty" +
                        " constructor.");
            } catch (IllegalAccessException e) {
                JqLog.e(e, "Cannot create an instance of " + klass + ". Make sure it has a public" +
                        " empty constructor.");
            }
        }
        return null;
    }
    public void release(Message message) {
        final Type type = message.type;
        message.recycle();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (type) {
            if (counts[type.ordinal()] < CACHE_LIMIT) {
                message.next = pools[type.ordinal()];
                pools[type.ordinal()] = message;
                counts[type.ordinal()] += 1;
            }
        }
    }
}
