package com.path.android.jobqueue.examples.twitter.activities;

import android.app.Activity;

public class BaseActivity extends Activity {
    private boolean visible = false;

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }
}
