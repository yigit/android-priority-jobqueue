- [android-priority-jobqueue (Job Manager)](#android-priority-jobqueue-job-manager)
  - [Why ?](#why-)
  - [show me the code](#show-me-the-code)
  - [What Happened?](#what-happened)
  - [Advantages of using Job Manager](#advantages-of-using-job-manager)
  - [Getting Started](#getting-started)
  - [Building](#building)
   - [Running Tests](#running-tests)
  - [getting coverage report](#getting-coverage-report)
  - [License](#license)

android-priority-jobqueue (Job Manager)
=========================

Priority JobQueue is an implementation of [Job Queue](http://en.wikipedia.org/wiki/Job_queue) specifically written for Android where you can easily schedule jobs (tasks) you want to run on the background to improve UX and application stability.

It is written [flexibility](https://github.com/path/android-priority-jobqueue/wiki/Job-Manager-Configuration) & functionality in mind, not performance but we'll improve performance once API and functionality list gets more stable.

### Why ?
Good client applications cache as much data as possible on the client side to provide best user experience. They make changes locally to reflect user actions instantly in a consistent way and sync data with server silently, whenever possible. 
This creates a case where you have a bunch of resource heavy operations (web requests, post processing etc) fighing for network and CPU on the device. After a while, it becomes really hard to schedule and prioritize all of these tasks. Job Queue comes handy in these cases where you can schedule your operations (jobs) with great flexibility.

Most of the idea is based on [Google IO 2010 talk on REST client applications][8].
Although it is not required, it is most useful when used with an event bus and a dependency injection framework.

### show me the code

Since an example is worth thousands of words, here is a simplified example. ([full version](https://github.com/path/android-priority-jobqueue/wiki/complete-job-example))

File: SendTweetJob.java
``` java
// a job to send out a tweet
public class PostTweetJob extends BaseJob implements Serializeable {
    private String text;
    public PostTweetJob(String text) {
        //requires network to run & persistent
        super(true, true);
    }
    @Override
    public void onAdded() {
        //job has been secured to disk. a good time to dispatch an event for UI
    }
    @Override
    public void onRun() throws Throwable {
        webservice.postTweet(text);
        //tweet has been sent to Twitter, a good time to dispatch an event for UI
    }
    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        //some exception happened in onRun, lets handle it and decide if we want to retry
    }
    @Override
    protected void onCancel() {
        //callback if job is dismissed due to failures.
        //it might have reached retry limit or shouldReRunOnThrowable might have returned false
    }
}


```

File: TweetActivity.java
``` java
//...
public void onSendClick() {
    final String status = editText.getText();
    editText.setText("");
    //we still call job manager in an async task because it will sync job to disk.
    new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
            jobManager.addJob(1, new PostTweetJob(status)); 
        }
    }.execute();
}
...
```


This is it :). 

* No network call in activity bound async tasks
* No serialization mess for important requests
* No network check or retry logic

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
* You can group jobs to ensure their serial execution, if necessary. For example, assume you have a messaging client and your user sent a bunch of messages when their phone had no coverage. When creating `SendMessageToNetwork` jobs, you can group them by conversation id (or receiver id). This way, messages sent to the same conversation will go in the order they are enqueued while messages sent to different conversations can still be sent it parallel. This will let you maximize network utilization and ensure data integrity w/o any effort on your side.
* By default, Job Manager checks for network (so you don't need to worry) and it won't run your network-requiring jobs unless there is a connection. You can even provide a custom [NetworkUtil][1] if you need custom logic (e.g. you can create another instance of job manager which runs only if there is a wireless connection)
* It is fairly unit tested and mostly documented. You can check [code coverage report][3] and [javadoc][4].


### Getting Started
* [Download latest jar][5]
* [check sample app][6]
* [check sample configuration][7]

### Building
* checkout the repo
* `> cd jobqueue`
* `> ant clean build-jar`
this will create a jar file under _release_ folder.

#### Running Tests
* > `cd jobqueue`
* > `ant clean test`

#### getting coverage report

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
[5]: https://github.com/path/android-priority-jobqueue/releases
[6]: https://github.com/path/android-priority-jobqueue/tree/master/examples
[7]: https://github.com/path/android-priority-jobqueue/blob/master/examples/twitter/TwitterClient/src/com/path/android/jobqueue/examples/twitter/TwitterApplication.java#L26
[8]: http://www.youtube.com/watch?v=xHXn3Kg2IQE
