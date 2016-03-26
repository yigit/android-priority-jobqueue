package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.network.NetworkUtil;

public class ConstraintChangeMessage extends Message {
    public ConstraintChangeMessage() {
        super(Type.CONSTRAINT_CHANGE);
    }

    @Override
    protected void onRecycled() {

    }
}
