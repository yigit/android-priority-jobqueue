package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.Collection;

public class TestConstraint extends Constraint {
    Timer timer;
    public TestConstraint(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void setShouldNotRequireNetwork(boolean shouldNotRequireNetwork) {
        super.setShouldNotRequireNetwork(shouldNotRequireNetwork);
    }

    @Override
    public void setTagConstraint(TagConstraint tagConstraint) {
        super.setTagConstraint(tagConstraint);
    }

    @Override
    public void setExcludeRunning(boolean excludeRunning) {
        super.setExcludeRunning(excludeRunning);
    }

    @Override
    public void setShouldNotRequireUnmeteredNetwork(boolean shouldNotRequireUnmeteredNetwork) {
        super.setShouldNotRequireUnmeteredNetwork(shouldNotRequireUnmeteredNetwork);
    }

    @Override
    public void setTags(String[] tags) {
        super.setTags(tags);
    }

    @Override
    public void setExcludeGroups(Collection<String> excludeGroups) {
        super.setExcludeGroups(excludeGroups);
    }

    @Override
    public void setExcludeJobIds(Collection<String> jobsIds) {
        super.setExcludeJobIds(jobsIds);
    }

    @Override
    public void clear() {
        super.clear();
    }

    @Override
    public long getNowInNs() {
        return timer.nanoTime();
    }

    @Override
    public void setTimeLimit(Long timeLimit) {
        super.setTimeLimit(timeLimit);
    }

    public static TestConstraint forTags(Timer timer, TagConstraint tagConstraint,
            Collection<String> excludeIds, String... tags) {
        TestConstraint constraint = new TestConstraint(timer);
        constraint.setTagConstraint(tagConstraint);
        constraint.setExcludeJobIds(excludeIds);
        constraint.setTags(tags);
        return constraint;
    }
}
