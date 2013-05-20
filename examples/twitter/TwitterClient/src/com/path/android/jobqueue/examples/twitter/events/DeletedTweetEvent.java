package com.path.android.jobqueue.examples.twitter.events;

public class DeletedTweetEvent {
    private long id;
    public DeletedTweetEvent(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
