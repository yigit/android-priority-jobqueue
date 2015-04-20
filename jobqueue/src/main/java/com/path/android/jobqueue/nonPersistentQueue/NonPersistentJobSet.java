package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.log.JqLog;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This is the default implementation of JobSet.
 * It uses TreeSet as the underlying data structure. Is currently inefficient, should be replaced w/ a more efficient
 * version
 */
public class NonPersistentJobSet implements JobSet {
    private final TreeSet<JobHolder> set;
    //groupId -> # of jobs in that group
    private final Map<String, Integer> existingGroups;
    private final Map<Long, JobHolder> idCache;
    private final Map<String, List<JobHolder>> tagCache;

    public NonPersistentJobSet(Comparator<JobHolder> comparator) {
        this.set = new TreeSet<JobHolder>(comparator);
        this.existingGroups = new HashMap<String, Integer>();
        this.idCache = new HashMap<Long, JobHolder>();
        this.tagCache = new HashMap<String, List<JobHolder>>();
    }

    private JobHolder safeFirst() {
        if(set.size() < 1) {
            return null;
        }
        return set.first();
    }

    @Override
    public JobHolder peek(Collection<String> excludeGroupIds) {
        if(excludeGroupIds == null || excludeGroupIds.size() == 0) {
            return safeFirst();
        }
        //there is an exclude list, we have to itereate :/
        for (JobHolder holder : set) {
            if (holder.getGroupId() == null) {
                return holder;
            }
            //we have to check if it is excluded
            if (excludeGroupIds.contains(holder.getGroupId())) {
                continue;
            }
            return holder;
        }
        return null;
    }

    private JobHolder safePeek() {
        if(set.size() == 0) {
            return null;
        }
        return safeFirst();
    }

    @Override
    public JobHolder poll(Collection<String> excludeGroupIds) {
        JobHolder peek = peek(excludeGroupIds);
        if(peek != null) {
            remove(peek);
        }
        return peek;
    }

    @Override
    public JobHolder findById(long id) {
        return idCache.get(id);
    }

    @Override
    public Set<JobHolder> findByTags(TagConstraint constraint, Collection<Long> exclude,
            String... tags) {
        if(tags == null) {
            return Collections.emptySet();
        }
        Set<JobHolder> jobs = new HashSet<JobHolder>();
        boolean first = true;
        for(String tag : tags) {
            List<JobHolder> found = tagCache.get(tag);
            if(found == null || found.size() == 0) {
                if (constraint == TagConstraint.ALL) {
                    return Collections.emptySet();
                } else {
                    continue;
                }
            }
            if (constraint == TagConstraint.ALL) {
                jobs.addAll(found);
            } else if (first) {
                jobs.addAll(found);
            } else {
                removeIfNotExists(jobs, found);
                if (jobs.size() == 0) {
                    return Collections.emptySet();
                }
            }
            first = false;
        }
        if (exclude != null) {
            removeIds(jobs, exclude);
        }
        return jobs;
    }

    private void removeIds(Set<JobHolder> mainSet, Collection<Long> ids) {
        final Iterator<JobHolder> itr = mainSet.iterator();
        while (itr.hasNext()) {
            JobHolder holder = itr.next();
            if (ids.contains(holder.getId())) {
                itr.remove();
            }
        }
    }
    private void removeIfNotExists(Set<JobHolder> mainSet, List<JobHolder> items) {
        final Iterator<JobHolder> itr = mainSet.iterator();
        while (itr.hasNext()) {
            JobHolder holder = itr.next();
            if (!items.contains(holder)) {
                itr.remove();
            }
        }
    }

    @Override
    public boolean offer(JobHolder holder) {
        if(holder.getId() == null) {
            throw new RuntimeException("cannot add job holder w/o an ID");
        }
        boolean result = set.add(holder);
        if(result == false) {
            //remove the existing element and add new one
            remove(holder);
            result = set.add(holder);
        }
        if(result) {
            idCache.put(holder.getId(), holder);
            addToTagCache(holder);
            if(holder.getGroupId() != null) {
                incGroupCount(holder.getGroupId());
            }
        }

        return result;
    }

    private void addToTagCache(JobHolder holder) {
        final Set<String> tags = holder.getTags();
        if(tags == null || tags.size() == 0) {
            return;
        }
        for(String tag : tags) {
            List<JobHolder> jobs = tagCache.get(tag);
            if(jobs == null) {
                jobs = new LinkedList<JobHolder>();
                tagCache.put(tag, jobs);
            }
            jobs.add(holder);
        }
    }

    private void removeFromTagCache(JobHolder holder) {
        final Set<String> tags = holder.getTags();
        if(tags == null || tags.size() == 0) {
            return;
        }
        for(String tag : tags) {
            List<JobHolder> jobs = tagCache.get(tag);
            if(jobs == null) {
                JqLog.e("trying to remove job from tag cache but cannot find the tag cache");
                return;
            }
            if(jobs.remove(holder) == false) {
                JqLog.e("trying to remove job from tag cache but cannot find it in the cache");
            } else if(jobs.size() == 0) {
                tagCache.remove(tag); // TODO pool?
            }

        }
    }


    private void incGroupCount(String groupId) {
        if(existingGroups.containsKey(groupId) == false) {
            existingGroups.put(groupId, 1);
        } else {
            existingGroups.put(groupId, existingGroups.get(groupId) + 1);
        }
    }

    private void decGroupCount(String groupId) {
        Integer val = existingGroups.get(groupId);
        if(val == null || val <= 0) {
            //TODO should we crash?
            JqLog.e("detected inconsistency in NonPersistentJobSet's group id hash. Please report " +
                    "a bug");
            existingGroups.remove(groupId);
            return;
        }
        val -= 1;
        if(val == 0) {
            existingGroups.remove(groupId);
        } else {
            existingGroups.put(groupId, val);
        }
    }

    @Override
    public boolean remove(JobHolder holder) {
        boolean removed = set.remove(holder);
        if(removed) {
            idCache.remove(holder.getId());
            removeFromTagCache(holder);
            if(holder.getGroupId() != null) {
                decGroupCount(holder.getGroupId());
            }
        }
        return removed;
    }



    @Override
    public void clear() {
        set.clear();
        existingGroups.clear();
        idCache.clear();
        tagCache.clear();
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(long now, Collection<String> excludeGroups) {
        //TODO we can cache most of this
        int total = 0;
        int groupCnt = existingGroups.keySet().size();
        Set<String> groupIdSet = null;
        if(groupCnt > 0) {
            groupIdSet = new HashSet<String>();//we have to track :/
        }
        for(JobHolder holder : set) {
            if(holder.getDelayUntilNs() < now) {
                //we should not need to check groupCnt but what if sth is wrong in hashmap, be defensive till
                //we write unit tests around NonPersistentJobSet
                if(holder.getGroupId() != null) {
                    if(excludeGroups != null && excludeGroups.contains(holder.getGroupId())) {
                        continue;
                    }
                    //we should not need to check groupCnt but what if sth is wrong in hashmap, be defensive till
                    //we write unit tests around NonPersistentJobSet
                    if(groupCnt > 0) {
                        if(groupIdSet.add(holder.getGroupId())) {
                            total++;
                        }
                    }
                    //else skip, we already counted this group
                } else {
                    total ++;
                }
            }
        }
        return new CountWithGroupIdsResult(total, groupIdSet);
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(Collection <String> excludeGroups) {
        if(existingGroups.size() == 0) {
            return new CountWithGroupIdsResult(set.size(), null);
        } else {
            //todo we can actually count from existingGroups set if we start counting numbers there as well
            int total = 0;
            Set<String> existingGroupIds = null;
            for(JobHolder holder : set) {
                if(holder.getGroupId() != null) {
                    if(excludeGroups != null && excludeGroups.contains(holder.getGroupId())) {
                        continue;
                    } else if(existingGroupIds == null) {
                        existingGroupIds = new HashSet<String>();
                        existingGroupIds.add(holder.getGroupId());
                    } else if(existingGroupIds.add(holder.getGroupId()) == false) {
                        continue;
                    }

                }
                total ++;
            }
            return new CountWithGroupIdsResult(total, existingGroupIds);
        }

    }
}
