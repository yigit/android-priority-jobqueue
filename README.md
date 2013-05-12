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
        jobManager.addJob(SendTweetJob.PRIORITY, new SendTweetJob(status));
    });
}
...
```

This is it :). No more async tasks, no more shared preferences mess. Here is what happened:

### What Happened?
* When user clicked send button, `onSendClick` method was called which creates a `SendTweetJob` and adds it to `JobManager` for execution.
It runs on a background thread because JobManager will make a disk access to add the job.

* Right after Job is syncronized to database, JobManager calls DependencyInjector (if provided) which will inject fields into our job class.
On `onAdded` callback, we saved tweet into disk and dispatched necessary event so that UI can update itself. Since there is no disk
access during this flow, it will be in a fraction of seconds so that user will see their Tweet on their UI instantly.

* When the job's turn comes, job manager will call `onRun` (and it will only be called if there is an active network connection). 
By default, JobManager users a simple connection utility that checks ConnectivityManager. You can provide a [custom one][1] which can
add additional checks (e.g. your server stability). You should also privde a [network util][1] which can notify JobManager when network
is recovered so that JobManager will avoid a busy loop and can decrease # of consumers. 

* JobManager will keep calling onRun until it succeeds (or it reaches retry limit). If an `onRun` method throws an exception,
JobManager will call `shouldReRunOnThrowable` so that you can handle the exception and decide if you should try again or not.

* If all retry attempts fail (or `shouldReRunOnThrowable` returns false), JobManager will call `onCancel` so that you can clean
your database, inform the user etc.

### Advantages of using Job Manager
* It is very easy to extract application logic from your activites, making your code more robust, easy to refactor and easy to **test**.
* You don't deal with asnyc tasks lifecycles etc. This is partially true assuming you use some eventbus to update your UI (you should).
At Path, we use [GreenRobot's Eventbus](github.com/greenrobot/EventBus), you can also go with your own favorite. (e.g. [Square's Otto] (https://github.com/square/otto))
* Job manager takes care of prioritizing jobs, checking network connection, running them in parallel etc. Especially, prioritization is very helpful if you have a 
resource heavy app like ours.
* You can delay jobs. This is helpful in cases like sending GCM token to server. It is a very ccommon task to acquire a GCM
token and send it to server when user logs into your app. You surely don't want it to interfere with other network operations (e.g. fetching
friend list). 

### License
```
Copyright 2013 Path, Inc.
Copyright 2010 Google, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

[1]: https://github.com/path/android-priority-jobqueue/blob/master/jobqueue/src/com/path/android/jobqueue/network/NetworkUtil.java
[2]: https://github.com/path/android-priority-jobqueue/blob/master/jobqueue/src/com/path/android/jobqueue/network/NetworkEventProvider.java
