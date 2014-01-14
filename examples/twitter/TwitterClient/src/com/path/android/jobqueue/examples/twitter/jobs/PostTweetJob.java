package com.path.android.jobqueue.examples.twitter.jobs;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.examples.twitter.controllers.TwitterController;
import com.path.android.jobqueue.examples.twitter.entities.Tweet;
import com.path.android.jobqueue.examples.twitter.events.DeletedTweetEvent;
import com.path.android.jobqueue.examples.twitter.events.PostedTweetEvent;
import com.path.android.jobqueue.examples.twitter.events.PostingTweetEvent;
import com.path.android.jobqueue.examples.twitter.models.TweetModel;
import de.greenrobot.event.EventBus;
import twitter4j.Status;
import twitter4j.TwitterException;

import java.util.Date;

public class PostTweetJob extends Job {
    private long localId;
    private String text;
    public PostTweetJob(String text) {
        super(new Params(Priority.MID).requireNetwork().persist().groupBy("post_tweet"));//order of tweets matter, we don't want to send two in parallel
        //use a negative id so that it cannot collide w/ twitter ids
        //we have to set local id here so it gets serialized into job (to find tweet later on)
        localId = -System.currentTimeMillis();
        this.text = text;
    }

    @Override
    public void onAdded() {
        //job has been secured to disk, add item to database
        try {
            Tweet tweet = new Tweet(
                    localId,
                    null,
                    text,
                    TwitterController.getInstance().getUserId(),
                    null,
                    new Date(System.currentTimeMillis())
            );
            TweetModel.getInstance().insertOrReplace(tweet);
            EventBus.getDefault().post(new PostingTweetEvent(tweet));
        } catch (TwitterException exception) {
            //if we cannot get user id, we won't add it locally for now.
        }
    }

    @Override
    public void onRun() throws Throwable {
        Status status = TwitterController.getInstance().postTweet(text);
        Tweet newTweet = new Tweet(status);
        TweetModel tweetModel = TweetModel.getInstance();
        Tweet existingTweet = tweetModel.getTweetByLocalId(localId);
        if(existingTweet != null) {
            existingTweet.updateNotNull(newTweet);
            //don't set local to false. this way, next time we ask for history update, we'll send proper tweet id
            tweetModel.insertOrReplace(existingTweet);
        } else {
            //somewhat local tweet does not exist. we might have crashed before onAdded is called.
            //just insert as if it is a new tweet
            tweetModel.insertOrReplace(newTweet);
        }
        EventBus.getDefault().post(new PostedTweetEvent(newTweet, localId));
    }

    @Override
    protected void onCancel() {
        //delete local tweet
        Tweet localTweet = TweetModel.getInstance().getTweetByLocalId(localId);
        if(localTweet != null) {
            TweetModel.getInstance().deleteTweetById(localId);
            EventBus.getDefault().post(new DeletedTweetEvent(localId));
        }
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        if(throwable instanceof TwitterException) {
            //if it is a 4xx error, stop
            TwitterException twitterException = (TwitterException) throwable;
            return twitterException.getStatusCode() < 400 || twitterException.getStatusCode() > 499;
        }
        return true;
    }
}
