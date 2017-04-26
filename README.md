### V2 is here!
There is a major internal rewrite of this project for more stability and new features. If you were using v1, see the migration guide here: [migration from v1 to v2](https://github.com/yigit/android-priority-jobqueue/wiki/V1-to-V2-migration)

``` gradle
dependencies {
    compile 'com.birbit:android-priority-jobqueue:2.0.1'
}
```

### Master Build Status
[![CircleCI](https://circleci.com/gh/yigit/android-priority-jobqueue/tree/master.svg?style=svg)](https://circleci.com/gh/yigit/android-priority-jobqueue/tree/master)
[![codecov](https://codecov.io/gh/yigit/android-priority-jobqueue/branch/master/graph/badge.svg)](https://codecov.io/gh/yigit/android-priority-jobqueue)

Android Priority Job Queue (Job Manager)
==========================

Priority Job Queue is an implementation of a [Job Queue](http://en.wikipedia.org/wiki/Job_queue) specifically written for Android to easily schedule jobs (tasks) that run in the background, improving UX and application stability.

It is written primarily with [flexibility][10] & [functionality][11] in mind. This is an ongoing project, which we will continue to add stability and performance improvements.

  - [Why ?](#why-)
   - [The Problem](#the-problem)
   - [Our Solution](#our-solution)
  - [Show me the code](#show-me-the-code)
  - [Priority Job Queue vs Job Scheduler vs GCMNetworkManager vs ?](#priority-job-queue-vs-job-scheduler-vs-gcmnetworkmanager-vs-)
  - [What's happening under the hood?](#under-the-hood)
  - [Advantages](#advantages)
  - [Getting Started](#getting-started)
  - [Version History](#version-history)
  - [Running Tests](#running-tests)
  - [wiki][9]
  - [Dependencies](#dependencies)
  - [License](#license)


### Why ?
#### The Problem
Almost every application does work in a background thread. These "background tasks" are expected to keep the application responsive and robust, especially during unfavorable situations (e.g. limited network connectivity). In Android applications, there are several ways to implement background work:

 * **Async Task:** Using an async task is the simplest approach, but it is tightly coupled with the activity lifecycle. If the activity dies (or is re-created), any ongoing async task will become wasted cycles or otherwise create unexpected behavior upon returning to the main thread. In addition, it is a terrible idea to drop a response from a network request just because a user rotated his/her phone.
 * **Loaders:** Loaders are a better option, as they recover themselves after a configuration change. On the other hand, they are designed to load data from disk and are not well suited for long-running network requests.
 * **Service with a Thread Pool:** Using a service is a much better solution, as it de-couples business logic from your UI. However, you will need a thread pool (e.g. ThreadPoolExecutor) to process requests in parallel, broadcast events to update the UI, and write additional code to persist queued requests to disk. As your application grows, the number of background operations grows, which force you to consider task prioritization and often-complicated concurrency problems.

#### Our Solution
Job Queue provides you a nice framework to do all of the above and more. You define your background tasks as [Jobs][11] and enqueue them to your [JobManager][10] instance. Job Manager will take care of prioritization, persistence, load balancing, delaying, network control, grouping etc. It also provides a nice lifecycle for your jobs to provide a better, consistent user experience.

Although not required, it is most useful when used with an event bus. It also supports dependency injection.

* Job Queue was inspired by a [Google I/O 2010 talk on REST client applications][8].

### Show me the code

Since a code example is worth thousands of documentation pages, here it is.

File: [PostTweetJob.java](https://github.com/yigit/android-priority-jobqueue/blob/master/examples/twitter/TwitterClient/src/com/birbit/android/jobqueue/examples/twitter/jobs/PostTweetJob.java)
``` java
// A job to send a tweet
public class PostTweetJob extends Job {
    public static final int PRIORITY = 1;
    private String text;
    public PostTweetJob(String text) {
        // This job requires network connectivity,
        // and should be persisted in case the application exits before job is completed.
        super(new Params(PRIORITY).requireNetwork().persist());
    }
    @Override
    public void onAdded() {
        // Job has been saved to disk.
        // This is a good place to dispatch a UI event to indicate the job will eventually run.
        // In this example, it would be good to update the UI with the newly posted tweet.
    }
    @Override
    public void onRun() throws Throwable {
        // Job logic goes here. In this example, the network call to post to Twitter is done here.
        // All work done here should be synchronous, a job is removed from the queue once 
        // onRun() finishes.
        webservice.postTweet(text);
    }
    @Override
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount,
            int maxRunCount) {
        // An error occurred in onRun.
        // Return value determines whether this job should retry or cancel. You can further
        // specify a backoff strategy or change the job's priority. You can also apply the
        // delay to the whole group to preserve jobs' running order.
        return RetryConstraint.createExponentialBackoff(runCount, 1000);
    }
    @Override
    protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
        // Job has exceeded retry attempts or shouldReRunOnThrowable() has decided to cancel.
    }
}


```

File: [TweetActivity.java](https://github.com/yigit/android-priority-jobqueue/blob/master/examples/twitter/TwitterClient/src/com/birbit/android/jobqueue/examples/twitter/SampleTwitterClient.java#L53)
``` java
//...
public void onSendClick() {
    final String status = editText.getText().toString();
    if(status.trim().length() > 0) {
      jobManager.addJobInBackground(new PostTweetJob(status));
      editText.setText("");
    }
}
...
```


That's it. :) Job Manager allows you to enjoy:

* No network calls in activity-bound async tasks
* No serialization mess for important requests
* No "manual" implementation of network connectivity checks or retry logic

### Priority Job Queue vs Job Scheduler vs GCMNetworkManager vs ?
On Lollipop, Android introduced [JobScheduler][14] which is a system friendly way to run non-time-critical tasks. It makes your code cleaner, makes your app a good citizen of the ecosystem and it is backported via [GCMNetworkManager][15].

The first version of Job Queue was created approximately 2 years before Job Scheduler. The major difference is that Job Queue is designed to run **all of** your background tasks while Job Scheduler is designed only for those you can defer.

We've created Job Queue because we wanted to have more control over the non-ui-thread activity of our application. We needed a convenient way to prioritize them, persist them accross application restarts and group based on the resources they access. 

A good practice of using Job Queue is to write **all** of your network tasks as Jobs and use **AsyncTasks** for disk access (e.g. loading data from sqlite). If you have long running background operations (e.g. processing an image), it is also a good practice to use Job Queue.

Starting with **v2**, Job Queue can be integrated with JobScheduler or GCMNetworkManager. This integration allows Job Queue to wake up the aplication based on the criterias of the Jobs it has. You can see the details on the related [wiki][16] page. The Scheduler API is flexible such that you can implement a custom version of it if your target market does not have Google Play Services.

### Under the hood
* When user clicked the send button, `onSendClick()` was called, which creates a `PostTweetJob` and adds it to Job Queue for execution.
It runs on a background thread because Job Queue will make a disk access to persist the job.

* Right after `PostTweetJob` is synchronized to disk, Job Queue calls `DependencyInjector` (if provided) which will [inject fields](http://en.wikipedia.org/wiki/Dependency_injection) into our job instance.
At `PostTweetJob.onAdded()` callback, we saved `PostTweetJob` to disk. Since there has been no network access up to this point, the time between clicking the send button and reaching `onAdded()` is within fractions of a second. This allows the implementation of `onAdded()` to inform UI to display the newly sent tweet almost instantly, creating a "fast" user experience.
In V1, `onAdded()` is called on the thread job was added.
In V2, `onAdded()` is called in JobManager's own thread.

* When it's time for `PostTweetJob` to run, Job Queue will call `onRun()` (and it will only be called if there is an active network connection, as dictated at the job's constructor).
By default, Job Queue uses a simple connection utility that checks `ConnectivityManager` (ensure you have `ACCESS_NETWORK_STATE` permission in your manifest). You can provide a [custom implementation][1] which can
add additional checks (e.g. your server stability). You should also provide a [`NetworkUtil`][1] which can notify Job Queue when network
is recovered so that Job Queue will avoid a busy loop and decrease # of consumers(default configuration does it for you).

* Job Queue will keep calling `onRun()` until it succeeds (or reaches a retry limit). If `onRun()` throws an exception,
Job Queue will call `shouldReRunOnThrowable()` to allow you to handle the exception and decide whether to retry job execution or abort.

* If all retry attempts fail (or when `shouldReRunOnThrowable()` returns false), Job Queue will call `onCancel()` to allow you to clean
your database, inform the user, etc.

### Advantages
* It is very easy to de-couple application logic from your activities, making your code more robust, easy to refactor, and easy to **test**.
* You don't have to deal with `AsyncTask` lifecycles. This is true assuming you use an event bus to update your UI (you should).
At Path, we use [greenrobot's EventBus](https://github.com/greenrobot/EventBus); however, you can also go with your favorite. (e.g. [Square's Otto] (https://github.com/square/otto))
* Job Queue takes care of prioritizing jobs, checking network connection, running them in parallel, etc. Job prioritization is especially indispensable when you have a resource-heavy app like ours.
* You can delay jobs. This is helpful in cases like sending a GCM token to your server. It is very common to acquire a GCM token and send it to your server when a user logs in to your app, but you don't want it to interfere with critical network operations (e.g. fetching user-facing content).
* You can group jobs to ensure their serial execution, if necessary. For example, assume you have a messaging client and your user sent a bunch of messages when their phone had no network coverage. When creating these `SendMessageToNetwork` jobs, you can group them by conversation ID. Through this approach, messages in the same conversation will send in the order they were enqueued, while messages between different conversations are still sent in parallel. This lets you effortlessly maximize network utilization and ensure data integrity.
* By default, Job Queue monitors network connectivity (so you don't need to worry about it). When a device is operating offline, jobs that require the network won't run until connectivity is restored. You can even provide a custom [`NetworkUtil`][1] if you need custom logic (e.g. you can create another instance of Job Queue which runs only if there is a wireless connection).
* It is unit tested and mostly documented. You can check our [code coverage report][3] and [Javadoc][4].


### Getting Started
We distribute artifacts through maven central repository.

Gradle: `compile 'com.birbit:android-priority-jobqueue:2.0.1'`

Maven:

``` xml
<dependency>
    <groupId>com.birbit</groupId>
    <artifactId>android-priority-jobqueue</artifactId>
    <version>2.0.1</version>
</dependency>
```

You can also [download][5] library jar, sources and Javadoc from Maven Central.

We highly recommend checking how you can configure job manager and individual jobs.
* [Configure job manager][10]
* [Configure individual jobs][11]
* [Review sample app][6]
* [Review sample configuration][7]

### Version History
  - 2.0.1 (Oct 11, 2016)
   - Fixed high CPU usage if queues are full (#262)
   - Handle bad input from job schedulers (#254)
  - 2.0.0 (Aug 14, 2016)
   - Validate JobManager ids
  - 2.0.0-beta3 (Aug 7, 2016)
   - Minor improvements in GCMNetworkManager
  - 2.0.0-beta2 (July 25, 2016)
   - Fixed a bug about cancelling persistent jobs (#212)
   - Fixed a cursor leak in the persistent queue (#206)
   - Added custom duration to the BatchingScheduler (#202)
  - 2.0.0-beta1 (June 27, 2016)
   - Commitment to the final 2.0 API and backward compatibility
   - Fixed a bug where cancel reason was not passed to onCancel if Job decides not to try again
  - 2.0.0-alpha5 (June 21, 2016)
   - Important fix with Framework Scheduler where it was not setting delay properly
  - 2.0.0-alpha4 (June 16, 2016) - Last planned alpha release (with api changes)
   - Introduced deadline parameter. Job will be run (or cancelled) when it hits the deadline regardless of its constraints.
   - Made it easier to write custom serializer by making base job fully transient.
   - Moved persistent job data to individual files which will lift the limit on the job size.
   - If Job serialization fails, JobManager will throw an exception.
  - 2.0.0-alpha3 (May 28, 2016)
   - New onCancel API receives the exception
   - Changed how schedulers are constructed to avoid NPE issues
   - New ThreadPool configuration to allow custom thread creation for consumers
  - 2.0.0-alpha1 (March 26, 2016)
   - A major rewrite with 70+ commits
   - [Migration guide][13]
  - 1.3.5 (Nov 7, 2015)
   - Default NetworkUtil is now Doze aware. (thanks @coltin)
   - RetryConstraint Delay can be applied to the group to preserve jobs' execution order. (#41)
  - 1.3.4 (Sept 12, 2015)
   - Fixed a potential ANR that was caused by sync on main thread. Issue #40
  - 1.3.3 (July 12, 2015)
   - Fixed default exponential backoff. Issue #33
  - 1.3.2 (July 5, 2015)
   - Added ability to change a Job's priority or add delay before it is retried. This mechanism can be used to add exponential backoff to jobs.
   - Added `Job#getApplicationContext` as a convenience method to get the Context inside a Job.
  - 1.3.1 (April 19, 2015)
   - Fixed issue #19 which was blocking a group forever if a job from that group is cancelled while running and then onRun fails.
   - Updated Robolectric version and moved all testing to Gradle.
   - Goodbye Cobertura, Welcome Jacoco!
  - 1.3 (March 23, 2015)
   - Ability to add tags to jobs. These tags can be used to later retrieve jobs.
   - Added long awaited job cancellation. You can use tags to cancel jobs.
   - Removed deprecated BaseJob class. This may break backward compatibility.
  - 1.1.2 (Feb 18, 2014)
   - Report exceptions to logger if addInBackground fails. (#31)
  - 1.1.1 (Feb 8, 2014)
   - Fixed an important bug (#35) where jobs in the same group may run in parallel if many of them become available at the same time while multiple consumer threads are waiting for a new job. 
  - 1.1 (Jan 30, 2014)
   - Job Status query API (#18)
   - Fixed a stackoverflow bug when network status changes after a long time. (#21) 
  - 1.0 (Jan 14, 2014):
   - Added [parameterized][12] constructor for Job for more readable code.
   - Deprecated `BaseJob` in favor of a more complete `Job` class.
  - 0.9.9 (Dec 16, 2013):
   - First public release.


### [Wiki][9]

### Dependencies
- Job Queue
- - [Support-v4](https://developer.android.com/topic/libraries/support-library/index.html)
- - [Okio](https://github.com/square/okio) ([license](https://github.com/square/okio/blob/master/LICENSE.txt))
- For testing, we use:
- - [Junit 4](http://junit.org/) ([license](https://github.com/junit-team/junit4/blob/master/LICENSE-junit.txt))
- - [Robolectric](http://robolectric.org/) ([license](https://github.com/robolectric/robolectric/blob/master/LICENSE.txt))
- - [Fest Util](http://easytesting.org/) ([license](http://www.apache.org/licenses/LICENSE-2.0))
- - [Hamcrest](https://code.google.com/p/hamcrest/) ([license](http://opensource.org/licenses/BSD-3-Clause))
- For code coverage report, we use:
- Sample Twitter client uses:
- - [Twitter4j](http://twitter4j.org/en)
- - [EventBus](https://github.com/greenrobot/EventBus)
- - [Path's fork of greenDAO](https://github.com/path/greenDAO) . ([original repo](https://github.com/greenrobot/greenDAO))
- - [Play Services GCM](https://developer.android.com/topic/libraries/support-library/features.html#v4) `compile 'com.google.android.gms:play-services-gcm:10.0.1'`

### Building

* Clone the repo
* `> cd jobqueue`
* `> ./gradlew clean assembleDebug assembleDebugUnitTest test`
*
This will create a jar file under _release_ folder.

#### Running Tests
* > `cd jobqueue`
* > `./gradlew clean check`


## License

Android Priority Jobqueue is made available under the [MIT license](http://opensource.org/licenses/MIT):

<pre>
The MIT License (MIT)

Copyright (c) 2013 Path, Inc.
Copyright (c) 2014 Google, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
</pre>


[1]: https://github.com/yigit/android-priority-jobqueue/blob/master/jobqueue/src/main/java/com/birbit/android/jobqueue/network/NetworkUtil.java
[2]: https://github.com/yigit/android-priority-jobqueue/blob/master/jobqueue/src/main/java/com/birbit/android/jobqueue/network/NetworkEventProvider.java
[3]: http://yigit.github.io/android-priority-jobqueue/coverage-report/index.html
[4]: http://yigit.github.io/android-priority-jobqueue/javadoc/index.html
[5]: http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22android-priority-jobqueue%22
[6]: https://github.com/yigit/android-priority-jobqueue/tree/master/examples
[7]: https://github.com/yigit/android-priority-jobqueue/blob/master/examples/twitter/TwitterClient/src/com/birbit/android/jobqueue/examples/twitter/TwitterApplication.java#L26
[8]: http://www.youtube.com/watch?v=xHXn3Kg2IQE
[9]: https://github.com/yigit/android-priority-jobqueue/wiki
[10]: https://github.com/yigit/android-priority-jobqueue/wiki/Job-Manager-Configuration
[11]: https://github.com/yigit/android-priority-jobqueue/wiki/Job-Configuration
[12]: https://github.com/yigit/android-priority-jobqueue/blob/master/jobqueue/src/main/java/com/birbit/android/jobqueue/Params.java
[13]: https://github.com/yigit/android-priority-jobqueue/wiki/V1-to-V2-migration
[14]: https://developer.android.com/reference/android/app/job/JobScheduler.html
[15]: https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager
[16]: https://github.com/yigit/android-priority-jobqueue/wiki/Integration-with-JobScheduler-and-GcmNetworkManager
