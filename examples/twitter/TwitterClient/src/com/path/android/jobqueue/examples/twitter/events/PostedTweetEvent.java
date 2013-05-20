package com.path.android.jobqueue.examples.twitter.events;

import com.path.android.jobqueue.examples.twitter.entities.Tweet;

public class PostedTweetEvent {
    private Tweet tweet;
    private long localId;

    public PostedTweetEvent(Tweet tweet, long localId) {
        this.tweet = tweet;
        this.localId = localId;
    }

    public Tweet getTweet() {
        return tweet;
    }

    public long getLocalId() {
        return localId;
    }
}
