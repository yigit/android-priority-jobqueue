package com.path.android.jobqueue.examples.twitter.jobs;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.examples.twitter.controllers.TwitterController;
import com.path.android.jobqueue.examples.twitter.entities.Tweet;
import com.path.android.jobqueue.examples.twitter.events.FetchedNewTweetsEvent;
import com.path.android.jobqueue.examples.twitter.models.TweetModel;
import de.greenrobot.event.EventBus;
import twitter4j.Status;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class FetchTweetsJob extends Job {
    private static final AtomicInteger jobCounter = new AtomicInteger(0);

    private final int id;
    public FetchTweetsJob() {
        super(new Params(Priority.LOW).requireNetwork().groupBy("fetch-tweets"));
        id = jobCounter.incrementAndGet();
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {
        if(id != jobCounter.get()) {
            //looks like other fetch jobs has been added after me. no reason to keep fetching
            //many times, cancel me, let the other one fetch tweets.
            return;
        }
        TweetModel tweetModel = TweetModel.getInstance();
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
