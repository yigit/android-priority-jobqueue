android-priority-jobqueue
=========================

Priority Jobqueue is an implementation of [Job Queue](http://en.wikipedia.org/wiki/Job_queue) specifically written for Android where you can easily schedule jobs (tasks) you want to run on the background to improve UX and application stability.

It is written [flexibility](configuring job manager) & functionality in mind, not performance but we'll improve performance once API and functionality list gets more stable.

Since an example is worth thousands of words, here it is:

File: SendTweetJob.java
``` java
// a job to send out a tweet
public class PostTweetJob extends BaseJob implements Serializeable {
    //if you are using dependency injection, you can configure JobManager to use it :)
    //since this job uses default Java Serialization for persistence, we marked injected fields as transient.
    //you can also provide your own serialization-deserialization logic.
    @Inject transient TweetModel tweetModel;
    @Inject transient Webservice webservice;
    @Inject transient UserModel userModel;
    @Inject transient EventBus eventBus;
    
    
    private long localId;
    private String text;
    public PostTweetJob(String text) {
        super(true, true);
        //use a negative id so that it cannot collide w/ twitter ids
        localId = -System.currentTimeMillis();
        this.text = text;
    }

    @Override
    public void onAdded() {
        //job has been secured to disk, add item to database so that we can display it to the user.
        Tweet tweet = new Tweet(
                localId,
                null,
                text,
                userModel.getMyUserId(),
                new Date(System.currentTimeMillis())
        );
        tweetModel.insert(tweet);
        eventBus.post(new PostingTweetEvent(tweet));
    }

    @Override
    public void onRun() throws Throwable {
        Status status = webservice.postTweet(text);
        Tweet newTweet = new Tweet(status);
        Tweet existingTweet = tweetModel.getTweetByLocalId(localId);
        if(existingTweet != null) {
            existingTweet.updateNotNull(newTweet);
            tweetModel.inserOrReplace(existingTweet);
        } else {
            //somewhat local tweet does not exist. we might have crashed before onAdded is called.
            //just insert as if it is a new tweet
            tweetModel.insertOrReplace(newTweet);
        }
        eventBus.post(new PostedTweetEvent(newTweet, localId));
    }

    @Override
    protected void onCancel() {
        //delete local tweet. we could not complete job successfully
        Tweet localTweet = tweetModel.getTweetByLocalId(localId);
        if(localTweet != null) {
            tweetModel.deleteTweetById(localId);
            eventBus.post(new DeletedTweetEvent(localId));
        }
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        if(throwable instanceof TwitterException) {
            //if it is a 4xx error, stop. job manager will call `onCancel`
            TwitterException twitterException = (TwitterException) throwable;
            return twitterException.getErrorCode() < 400 || twitterException.getErrorCode() > 499;
        }
        return true;
    }
}


```

File: TweetActivity.java
``` java
//...
public void onSendClick() {
    final String status = editText.getText();
    editText.setText("");
    //assume we have a ThreadUtil class to dispatch code to a shared background thread.
    //this avoids making a disk write on main thread.
    ThreadUtil.performOnBackgroundThread(new Runnable() {
        jobManager.addJob(1, new PostTweetJob(status));
    });
}

//....
public void onEventMainThread(PostingTweetEvent ignored) {
    //add tweet to list
}

public void onEventMainThread(PostedTweetEvent ignored) {
    //refresh tweet list
}
...
```

This is it :). No more async tasks, no more shared preferences mess. Here is what happened:

### What Happened?
* When user clicked send button, `onSendClick` method was called which creates a `PostTweetJob` and adds it to `JobManager` for execution.
It runs on a background thread because JobManager will make a disk access to persist the job.

* Right after Job is syncronized to database, JobManager calls DependencyInjector (if provided) which will inject fields into our job instance. 
On `onAdded` callback, we saved tweet into disk and dispatched necessary event so that UI can update itself. Since there is no network access during this flow, it will be in a fraction of seconds so that user will see their Tweet on their UI instantly.

* When the job's turn comes, job manager will call `onRun` (and it will only be called if there is an active network connection). 
By default, JobManager users a simple connection utility that checks ConnectivityManager (ensure you have `ACCESS_NETWORK_STATE` permission in your manifest). You can provide a [custom one][1] which can
add additional checks (e.g. your server stability). You should also provide a [network util][1] which can notify JobManager when network
is recovered so that JobManager will avoid a busy loop and can decrease # of consumers. 

* JobManager will keep calling onRun until it succeeds (or it reaches retry limit). If an `onRun` method throws an exception,
JobManager will call `shouldReRunOnThrowable` so that you can handle the exception and decide if you should try again or not.

* If all retry attempts fail (or `shouldReRunOnThrowable` returns false), JobManager will call `onCancel` so that you can clean
your database, inform the user etc.

### Advantages of using Job Manager
* It is very easy to de-couple application logic from your activites, making your code more robust, easy to refactor and easy to **test**.
* You don't deal with asnyc tasks lifecycles etc. This is partially true assuming that you use some eventbus to update your UI (you should).
At Path, we use [GreenRobot's Eventbus](github.com/greenrobot/EventBus), you can also go with your own favorite. (e.g. [Square's Otto] (https://github.com/square/otto))
* Job manager takes care of prioritizing jobs, checking network connection, running them in parallel etc. Especially, prioritization is very helpful if you have a resource heavy app like ours.
* You can delay jobs. This is helpful in cases like sending GCM token to server. It is a very common task to acquire a GCM token and send it to server when user logs into your app. You surely don't want it to interfere with other network operations (e.g. fetching important content updates).
* You can group jobs to ensure their serial execution, if necessary. For instance at Path, we group jobs related to a moment with moment's id so that we won't hit any concurrency problems due to editing same moment in multiple threads.

* It is fairly unit tested and mostly documented. You can check [code coverage report][3] and [javadoc][4].

### Building
* checkout the repo
* > cd jobqueue
* > ant clean build-jar
this will create a jar file under release folder.

# Running Tests
* > cd jobqueue
* > ant clean test

# getting coverage report

### TODO
* move to gradle.
* ability to cancel jobs by id
* change default network utility to listen for network events by default


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
[3]: http://path.github.io/android-priority-jobqueue/coverage-report/index.html
[4]: http://path.github.io/android-priority-jobqueue/javadoc/index.html
