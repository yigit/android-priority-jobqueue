package com.birbit.android.jobqueue;

import com.path.android.jobqueue.TagConstraint;

import java.util.Collection;

public class TestConstraint extends Constraint {

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
    public void setTimeLimit(Long timeLimit) {
        super.setTimeLimit(timeLimit);
    }

    public static TestConstraint forTags(TagConstraint tagConstraint, Collection<String> excludeIds,
            String... tags) {
        TestConstraint constraint = new TestConstraint();
        constraint.setTagConstraint(tagConstraint);
        constraint.setExcludeJobIds(excludeIds);
        constraint.setTags(tags);
        return constraint;
    }
}
