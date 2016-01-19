package com.birbit.android.jobqueue;

import com.path.android.jobqueue.TagConstraint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is used when querying JobQueues to fetch particular jobs.
 * <p>
 * JobQueues should not reference this class after the query method returns because Constraints are
 * re-used by the JobManager.
 */
public class Constraint {
    private boolean shouldNotRequireNetwork;
    private TagConstraint tagConstraint;
    private final Set<String> tags = new HashSet<>();
    private final List<String> excludeGroups = new ArrayList<>();
    private final List<String> excludeJobIds = new ArrayList<>();
    private boolean excludeRunning;
    private Long timeLimit;
    // the identifier we create from the given values
    private String uniqueId;
    private final StringBuilder uniqueIdBuilder = new StringBuilder();
    /**
     * Returns true if the network is currently available.
     *
     * @return True if network connection is currently available, false otherwise.
     */
    public boolean shouldNotRequireNetwork() {
        return shouldNotRequireNetwork;
    }

    /**
     * The tag constraint to be used while querying with tags.
     *
     * @return The tag constraint to be used or null if there is no tag constraints.
     * @see #getTags()
     */
    public TagConstraint getTagConstraint() {
        return tagConstraint;
    }

    /**
     * The set of tags. If this list is not-empty, {@link #getTagConstraint()} will have a non-null
     * result.
     *
     * @return The set of tags
     * @see #getTagConstraint()
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * The list of groups to be excluded. It is guaranteed to be ordered in natural string sorting
     * order.
     *
     * @return The set of job groups to exclude.
     */
    public List<String> getExcludeGroups() {
        return excludeGroups;
    }

    /**
     * Returns true if running jobs should be excluded from the query
     * @return True if running jobs should be excluded
     */
    public boolean excludeRunning() {
        return excludeRunning;
    }

    /**
     * Exclude jobs whose run time is after this time. Might be null if there is no time limit.
     * @return Time in NS which should be used to filter out delayed jobs
     */
    public Long getTimeLimit() {
        return timeLimit;
    }

    /**
     * The list of jobs ids that should be excluded from the result
     * @return The list of job ids that should be excluded from the result
     */
    public List<String> getExcludeJobIds() {
        return excludeJobIds;
    }

    void setShouldNotRequireNetwork(boolean shouldNotRequireNetwork) {
        this.shouldNotRequireNetwork = shouldNotRequireNetwork;
    }

    void setTagConstraint(TagConstraint tagConstraint) {
        this.tagConstraint = tagConstraint;
    }

    void setExcludeRunning(boolean excludeRunning) {
        this.excludeRunning = excludeRunning;
    }

    void setTags(String[] tags) {
        this.tags.clear();
        if (tags != null) {
            Collections.addAll(this.tags, tags);
        }
    }

    void setExcludeGroups(Collection<String> excludeGroups) {
        this.excludeGroups.clear();
        if (excludeGroups != null) {
            this.excludeGroups.addAll(excludeGroups);
        }
    }

    void setExcludeJobIds(Collection<String> jobsIds) {
        this.excludeJobIds.clear();
        if (jobsIds != null) {
            this.excludeJobIds.addAll(jobsIds);
        }
    }

    void setTimeLimit(Long timeLimit) {
        this.timeLimit = timeLimit;
    }

    void clear() {
        shouldNotRequireNetwork = false;
        tagConstraint = null;
        tags.clear();
        excludeGroups.clear();
        uniqueId = null;
        excludeJobIds.clear();
        excludeRunning = false;
        timeLimit = null;
    }

    public String getUniqueId() {
        if (uniqueId != null) {
            return uniqueId;
        }
        uniqueIdBuilder.setLength(0);
        uniqueIdBuilder.append(shouldNotRequireNetwork ? "1" : "0");
        uniqueIdBuilder.append(tagConstraint == null ? "_" : tagConstraint.ordinal());
        uniqueIdBuilder.append(excludeRunning ? "1" : "0");
        if (timeLimit == null) {
            uniqueIdBuilder.append("_");
        } else {
            uniqueIdBuilder.append(timeLimit);
        }
        uniqueIdBuilder.append("T");
        for (String tag : tags) {
            uniqueIdBuilder.append(tag).append(",");
        }
        uniqueIdBuilder.append("E");
        for (String groupId : excludeGroups) {
            uniqueIdBuilder.append(groupId).append(",");
        }
        uniqueIdBuilder.append("J");
        for (String jobId : excludeJobIds) {
            uniqueIdBuilder.append(jobId).append(",");
        }
        uniqueId = uniqueIdBuilder.toString();
        return uniqueId;
    }
}
