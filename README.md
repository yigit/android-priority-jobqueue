android-priority-jobqueue
=========================

Priority Jobqueue is an implementation of [Job Queue](http://en.wikipedia.org/wiki/Job_queue) specifically written for Android where you can easily schedule jobs (tasks) you want to run on the background to improve UX and application stability.

Since an example is worth thousans of works, here it is:

File: SendTweetJob.java
``` java
// a job to send out a tweet
public class SendTweetJob extends BaseJob {
    public static final int PRIORITY = 5;//so custom priority depending on your codebase
    //you can injections on your jobs
    //we keep then transient because we don't want these methods serialized into databse
    @Inject transient TweetModel tweetModel;
    @Inject transient EventBus eventbus;
    @Inject transient TwitterClient twitterClient;
    private Tweet tweet;
    public SendTweetJob(String status) {
        super(true);//true-> requires network. This job should not run if we don't have network connection
        tweet = new Tweet(status);
        tweet.setLocalId(generateLocalId());
        tweet.setPending(true);
    }

    @Override
    public void onAdded() {
        //called when job is syncronized to disk. This means it will eventually run so we can update database here and
        //show tweet to the user as if it went out
        tweetModel.save(tweet);
        //dispatch an envet so that if user is viewing tweets, activity will listen to this event and refresh itself
        eventbus.post(new SendingTweetEvent(tweet));
    }

    @Override
    public void onRun() throws Throwable {
        twitterClient.postStatus(tweet);
        tweet.setPending(false);
        tweetModel.save(tweet);
        //since twitter request succeeded (otherwise it would dispatch an error) notify eventbus so that UI can update itself.
        eventbus.post(new SentTweetEvent(tweet));
    }

    @Override
    public boolean shouldPersist() {
        return true;//this job should be persisted into databse because we don't want to lose user's tweet
    }

    @Override
    protected void onCancel() {
        //for some reason, tweet should not be sycned to twitter. we should delete the local one
        //might be nice to show a notification etc to the user to retry their tweet (or maybe re-authenticate)
        tweetModel.delete(tweet);
        eventbus.post(new FaliedToSendTweet(tweet));
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        //onRun method did throw and error. we should handle what it is and see if we can try to re-send
        if (throwable instanceof HttpResponseException) {
            int statusCode = ((HttpResponseException) e).getStatusCode();
            if(statusCode < 500 && statusCode >=400) {
                return false;//wen cannot recover from 4xx error
            }
        }
        return true; //retry. a more complete example would check more error conditions
    }
}

```

File: TweetActivity.java
``` java
...
public void onSendClick() {
    final String status = editText.getText();
    editText.setText("");
    //assume we have a ThreadUtil class to dispatch code to a shared background thread
    ThreadUtil.performOnBackgroundThread(new Runnable() {
        jobManager.addJob(SendTweetJob.PRIORITY, new SendTweetJob(status);
    });
}
...
```




