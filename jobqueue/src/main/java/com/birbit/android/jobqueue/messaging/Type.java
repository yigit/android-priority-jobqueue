package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CallbackMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.ConstraintChangeMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * All message types
 */
public enum Type {
    CALLBACK(CallbackMessage.class, 0),
    RUN_JOB(RunJobMessage.class, 0),
    COMMAND(CommandMessage.class, 0),
    JOB_CONSUMER_IDLE(JobConsumerIdleMessage.class, 0), // MUST ARRIVE AFTER JOB RESULT
    ADD_JOB(AddJobMessage.class, 1),
    CONSTRAINT_CHANGE(ConstraintChangeMessage.class, 2),
    RUN_JOB_RESULT(RunJobResultMessage.class, 3);
    final Class<? extends Message> klass;
    final static Map<Class<? extends Message>, Type> mapping;
    final int priority; // higher is better
    final static int MAX_PRIORITY;

    Type(Class<? extends Message> klass, int priority) {
        this.klass = klass;
        this.priority = priority;
    }
    static {
        int maxPriority = 0;
        mapping = new HashMap<>();
        for (Type type : Type.values()) {
            mapping.put(type.klass, type);
            if (type.priority > maxPriority) {
                maxPriority = type.priority;
            }
        }
        MAX_PRIORITY = maxPriority;
    }
}
