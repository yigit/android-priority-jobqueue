package com.path.android.jobqueue.nonPersistentQueue;

import java.util.Set;

public class CountWithGroupIdsResult {
    private int count;
    private Set<String> groupIds;

    public CountWithGroupIdsResult(int count, Set<String> groupIds) {
        this.count = count;
        this.groupIds = groupIds;
    }

    public int getCount() {
        return count;
    }

    //nullable
    public Set<String> getGroupIds() {
        return groupIds;
    }

    public CountWithGroupIdsResult mergeWith(CountWithGroupIdsResult other) {
        if(groupIds == null || other.groupIds == null) {
            this.count += other.count;
            if(groupIds == null) {
                groupIds = other.groupIds;
            }
            return this;
        }
        //there are some groups, we need to find if any group is in both lists
        int sharedGroups = 0;
        for(String groupId : other.groupIds) {
            if(groupIds.add(groupId) == false) {
                sharedGroups ++;
            }
        }
        count = count + other.count - sharedGroups;
        return this;
    }
}
