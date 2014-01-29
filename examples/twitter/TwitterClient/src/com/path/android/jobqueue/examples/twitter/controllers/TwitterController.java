package com.path.android.jobqueue.examples.twitter.controllers;

import com.path.android.jobqueue.examples.twitter.Config;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

import java.util.List;

public class TwitterController {
    private static TwitterController instance;
    private Twitter twitter;
    private Long userId;
    public static final int PAGE_LENGTH = 20;


    public synchronized static TwitterController getInstance() {
        if(instance == null) {
            instance = new TwitterController();
        }
        return instance;
    }

    public TwitterController() {
        twitter = new TwitterFactory(new ConfigurationBuilder()
        .setOAuthAccessToken(Config.ACCESS_TOKEN)
        .setOAuthAccessTokenSecret(Config.ACCESS_TOKEN_SECRET)
        .setDebugEnabled(true)
        .setOAuthConsumerKey(Config.CONSUMER_KEY)
        .setOAuthConsumerKey(Config.CONSUMER_SECRET)
        .build()).getSingleton();
        AccessToken accessToken = new AccessToken(Config.ACCESS_TOKEN, Config.ACCESS_TOKEN_SECRET);
        twitter.setOAuthConsumer(Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
        twitter.setOAuthAccessToken(accessToken);
    }

    public List<Status> loadTweets(Long sinceId) throws TwitterException {
        Paging paging = new Paging();
        paging.setCount(PAGE_LENGTH);
        if(sinceId != null) {
            paging.setSinceId(sinceId);
        }
        return twitter.getHomeTimeline(paging);
    }

    public Status postTweet(String status) throws TwitterException {
        return twitter.updateStatus(status);
    }

    public long getUserId() throws TwitterException {
        if(userId == null) {
            userId = twitter.getId();
        }
        return userId;

    }
}
