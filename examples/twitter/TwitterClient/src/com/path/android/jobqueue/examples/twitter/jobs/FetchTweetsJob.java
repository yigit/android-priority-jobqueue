package com.path.android.jobqueue.examples.twitter.jobs;

import android.util.Log;
import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.examples.twitter.controllers.TwitterController;
import com.path.android.jobqueue.examples.twitter.entities.Tweet;
import com.path.android.jobqueue.examples.twitter.events.FetchedNewTweetsEvent;
import com.path.android.jobqueue.examples.twitter.models.TweetModel;
import de.greenrobot.event.EventBus;
import twitter4j.Status;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.List;


public class FetchTweetsJob extends BaseJob {
    public FetchTweetsJob() {
        super(true);
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {
        TweetModel tweetModel = new TweetModel();
        Tweet lastTweet = tweetModel.getLastTweet();
        List<Status> statusList = TwitterController.getInstance().loadTweets(lastTweet == null ? null : lastTweet.getServerId());
        if(statusList.size() > 0) {
            List<Tweet> tweets = new ArrayList<Tweet>(statusList.size());
            for(Status status : statusList) {
                Tweet tweet = new Tweet(status);
                tweets.add(tweet);
            }
            tweetModel.insertOrReplaceAll(tweets);
            EventBus.getDefault().post(new FetchedNewTweetsEvent());
        }
    }

    @Override
    public boolean shouldPersist() {
        return false;
    }

    @Override
    protected void onCancel() {
        //TODO show error notification
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        if(throwable instanceof TwitterException) {
            //if it is a 4xx error, stop
            TwitterException twitterException = (TwitterException) throwable;
            return twitterException.getErrorCode() < 400 || twitterException.getErrorCode() > 499;
        }
        return true;
    }
}
