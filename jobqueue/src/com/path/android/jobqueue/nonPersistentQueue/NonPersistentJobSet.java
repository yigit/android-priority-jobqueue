package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.log.JqLog;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    public NonPersistentJobSet(Comparator<JobHolder> comparator) {
        this.set = new TreeSet<JobHolder>(comparator);
        this.existingGroups = new HashMap<String, Integer>();
        this.idCache = new HashMap<Long, JobHolder>();
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
            if(holder.getGroupId() != null) {
                incGroupCount(holder.getGroupId());
            }
        }

        return result;
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
        if(val == null || val == 0) {
            //TODO should we crash?
            JqLog.e("detected inconsistency in NonPersistentJobSet's group id hash");
            return;
        }
        val -= 1;
        if(val == 0) {
            existingGroups.remove(groupId);
        }
    }

    @Override
    public boolean remove(JobHolder holder) {
        boolean removed = set.remove(holder);
        if(removed) {
            idCache.remove(holder.getId());
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
