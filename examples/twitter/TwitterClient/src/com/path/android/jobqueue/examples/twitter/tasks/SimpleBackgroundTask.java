package com.path.android.jobqueue.examples.twitter.tasks;

import android.app.Activity;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

abstract public class SimpleBackgroundTask<T> extends AsyncTask<Void, Void, T> {
    WeakReference<Activity> weakActivity;
    public SimpleBackgroundTask(Activity activity) {
        weakActivity = new WeakReference<Activity>(activity);
    }
    @Override
    protected final T doInBackground(Void... voids) {
        return onRun();
    }

    private boolean canContinue() {
        Activity activity = weakActivity.get();
        return activity != null && activity.isFinishing() == false;
    }

    @Override
    protected void onPostExecute(T t) {
        if(canContinue()) {
            onSuccess(t);
        }
    }

    abstract protected T onRun();

    abstract protected void onSuccess(T result);
}
