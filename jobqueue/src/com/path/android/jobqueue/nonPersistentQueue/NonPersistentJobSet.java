package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * This is the default implementation of JobSet.
 * It uses TreeSet as the underlying data structure. Is currently inefficient, should be replaced w/ a more efficient
 * version
 */
public class NonPersistentJobSet implements JobSet {
    TreeSet<JobHolder> set;

    public NonPersistentJobSet(Comparator<JobHolder> comparator) {
        this.set = new TreeSet<JobHolder>(comparator);
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
    public boolean offer(JobHolder holder) {
        if(holder.getId() == null) {
            throw new RuntimeException("cannot add job holder w/o an ID");
        }
        boolean result = set.add(holder);
        if(result == false) {
            //remove the existing element and add new one
            set.remove(holder);
            return set.add(holder);
        }
        return true;
    }

    @Override
    public boolean remove(JobHolder holder) {
        return set.remove(holder);
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public int size() {
        return set.size();
    }
}
