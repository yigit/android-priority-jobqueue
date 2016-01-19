package com.path.android.jobqueue.persistentQueue.sqlite;

public class Where {
    public final String query;
    public final String[] args;

    public Where(String query, String[] args) {
        this.query = query;
        this.args = args;
    }
}
