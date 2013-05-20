package com.path.android.jobqueue.examples.twitter.events;

import com.path.android.jobqueue.examples.twitter.entities.Tweet;

public class PostingTweetEvent {
    private Tweet tweeet;

    public PostingTweetEvent(Tweet tweeet) {
        this.tweeet = tweeet;
    }

    public Tweet getTweeet() {
        return tweeet;
    }
}
