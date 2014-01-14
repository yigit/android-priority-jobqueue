package com.path.android.jobqueue.examples.twitter;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.examples.twitter.activities.BaseActivity;
import com.path.android.jobqueue.examples.twitter.adapters.LazyListAdapter;
import com.path.android.jobqueue.examples.twitter.entities.Tweet;
import com.path.android.jobqueue.examples.twitter.events.DeletedTweetEvent;
import com.path.android.jobqueue.examples.twitter.events.FetchedNewTweetsEvent;
import com.path.android.jobqueue.examples.twitter.events.PostedTweetEvent;
import com.path.android.jobqueue.examples.twitter.events.PostingTweetEvent;
import com.path.android.jobqueue.examples.twitter.jobs.FetchTweetsJob;
import com.path.android.jobqueue.examples.twitter.jobs.PostTweetJob;
import com.path.android.jobqueue.examples.twitter.models.TweetModel;
import com.path.android.jobqueue.examples.twitter.tasks.SimpleBackgroundTask;
import de.greenrobot.dao.LazyList;
import de.greenrobot.event.EventBus;

public class SampleTwitterClient extends BaseActivity {
    private TweetAdapter tweetAdapter;
    private boolean dataDirty = true;
    JobManager jobManager;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataDirty = true;
        setContentView(R.layout.main);
        jobManager = TwitterApplication.getInstance().getJobManager();
        ListView listView = (ListView) findViewById(R.id.tweet_list);
        tweetAdapter = new TweetAdapter(getLayoutInflater());
        findViewById(R.id.send_tweet).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText text = (EditText) findViewById(R.id.edit_status);
                if(text.getText().toString().trim().length() > 0) {
                    sendTweet(text.getText().toString());
                    text.setText("");
                }
            }
        });
        listView.setAdapter(tweetAdapter);
        EventBus.getDefault().register(this);
    }

    private void sendTweet(final String text) {
        jobManager.addJobInBackground(new PostTweetJob(text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(FetchedNewTweetsEvent ignored) {
        onUpdateEvent();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(PostingTweetEvent ignored) {
        //we could just add this to top or replace element instead of refreshing whole list
        onUpdateEvent();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(PostedTweetEvent ignored) {
        //we could just add this to top or replace element instead of refreshing whole list
        onUpdateEvent();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(DeletedTweetEvent ignored) {
        //we could just add this to top or replace element instead of refreshing whole list
        Toast.makeText(this, "cannot send the tweet", Toast.LENGTH_SHORT).show();
        onUpdateEvent();
    }

    private void onUpdateEvent() {
        if(isVisible()) {
            refreshList();
        } else {
            dataDirty = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        jobManager.addJobInBackground(new FetchTweetsJob());
        if(dataDirty) {
            refreshList();
            dataDirty = false;
        }
    }

    private void refreshList() {
        new SimpleBackgroundTask<LazyList<Tweet>>(this) {
            @Override
            protected LazyList<Tweet> onRun() {
                return TweetModel.getInstance().lazyLoadTweets();
            }

            @Override
            protected void onSuccess(LazyList<Tweet> result) {
                tweetAdapter.replaceLazyList(result);
            }
        }.execute();
    }

    private static class TweetAdapter extends LazyListAdapter<Tweet> {
        private final LayoutInflater layoutInflater;
        public TweetAdapter(LayoutInflater layoutInflater) {
            this.layoutInflater = layoutInflater;
        }
        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder;
            if(view == null) {
                view = layoutInflater.inflate(R.layout.list_tweet, viewGroup, false);
                holder = new ViewHolder(view);
            } else {
                holder = ViewHolder.getFromView(view);
            }
            holder.render(getItem(i));
            return view;
        }

        private static class ViewHolder {
            TextView statusTextView;
            public ViewHolder(View view) {
                statusTextView = (TextView) view.findViewById(R.id.status);
                view.setTag(this);
            }

            public static ViewHolder getFromView(View view) {
                Object tag = view.getTag();
                if(tag instanceof ViewHolder) {
                    return (ViewHolder) tag;
                } else {
                    return new ViewHolder(view);
                }
            }

            public void render(Tweet tweet) {
                statusTextView.setText(tweet.getText());
                if(tweet.getServerId() == null) {
                    statusTextView.setTextColor(Color.YELLOW);
                } else {
                    statusTextView.setTextColor(Color.WHITE);
                }
            }
        }
    }
}
