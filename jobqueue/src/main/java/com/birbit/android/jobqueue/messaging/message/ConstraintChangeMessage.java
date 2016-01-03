package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

public class ConstraintChangeMessage extends Message {
    private static final int TYPE_NETWORK = 1;
    private boolean booleanValue;
    private int constraintType;
    public ConstraintChangeMessage() {
        super(Type.CONSTRAINT_CHANGE);
    }

    @Override
    protected void onRecycled() {

    }

    public void setNetwork(boolean isConnected) {
        booleanValue = isConnected;
        constraintType = TYPE_NETWORK;
    }
}
