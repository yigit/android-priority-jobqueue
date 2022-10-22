package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobStatus;
import com.birbit.android.jobqueue.MapCallback;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

public class JobStatusByTagsMessage extends Message implements MapCallback.MessageWithCallback<Job, JobStatus> {
  public String[] tags;
  public MapCallback<Job, JobStatus> mapCallback;

  protected JobStatusByTagsMessage() {
    super(Type.JOB_STATUS_BY_TAGS);
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }

  @Override protected void onRecycled() {
    this.tags = null;
    this.mapCallback = null;
  }

  @Override public void setCallback(MapCallback<Job, JobStatus> mapCallback) {
    this.mapCallback = mapCallback;
  }
}
