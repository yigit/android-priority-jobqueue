package com.tarkalabs.android.jobqueue.messaging.message;

import com.tarkalabs.android.jobqueue.messaging.Message;
import com.tarkalabs.android.jobqueue.messaging.Type;
import com.tarkalabs.android.jobqueue.CancelResult;

public class CancelResultMessage extends Message {
    CancelResult.AsyncCancelCallback callback;
    CancelResult result;
    public CancelResultMessage() {
        super(Type.CANCEL_RESULT_CALLBACK);
    }

    @Override
    protected void onRecycled() {
        result = null;
        callback = null;
    }

    public void set(CancelResult.AsyncCancelCallback callback, CancelResult result) {
        this.callback = callback;
        this.result = result;
    }

    public CancelResult.AsyncCancelCallback getCallback() {
        return callback;
    }

    public CancelResult getResult() {
        return result;
    }
}
