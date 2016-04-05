package com.birbit.android.jobqueue.persistentQueue.sqlite;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.TagConstraint;

import android.support.v4.util.LruCache;

import java.util.Collection;

/**
 * Internal class to cache sql queries and statements.
 */
class WhereQueryCache {
    private static final int INT_SIZE = 6;
    private static final int BOOL_SIZE = 1;
    private static final int NETWORK = 0;
    private static final int UNMETERED_NETWORK = NETWORK + BOOL_SIZE;
    private static final int TAG_TYPE = UNMETERED_NETWORK + BOOL_SIZE;
    private static final int TAG_COUNT = TAG_TYPE + BOOL_SIZE + BOOL_SIZE;
    private static final int GROUP_COUNT = TAG_COUNT + INT_SIZE;
    private static final int JOB_COUNT = GROUP_COUNT + INT_SIZE;
    private static final int EXCLUDE_RUNNING = JOB_COUNT + INT_SIZE;
    private static final int TIME_LIMIT = EXCLUDE_RUNNING + BOOL_SIZE;
    private static final int PENDING_CANCELLATIONS = TIME_LIMIT + BOOL_SIZE;
    private static final int INT_LIMIT = 1 << INT_SIZE;

    // TODO implement some query cacheable check for queries that have way too many parameters
    private final LruCache<Long, Where> queryCache = new LruCache<Long, Where>(15) {
        @Override
        protected void entryRemoved(boolean evicted, Long key, Where oldValue, Where newValue) {
            oldValue.destroy();
        }
    };

    private final String sessionId;

    public WhereQueryCache(long sessionId) {
        this.sessionId = Long.toString(sessionId);
    }

    public Where build(Constraint constraint, Collection<String> pendingCancellations,
            StringBuilder stringBuilder) {
        final boolean cacheable = isCacheable(constraint);
        final long cacheKey = cacheKey(constraint, pendingCancellations);
        Where where = cacheable ? queryCache.get(cacheKey) : null;
        if (where == null) {
            // build it
            where = createWhere(cacheKey, constraint, pendingCancellations, stringBuilder);
            if (cacheable) {
                queryCache.put(cacheKey, where);
            }
        }
        fillWhere(constraint, where, pendingCancellations);
        return where;
    }

    private void fillWhere(Constraint constraint, Where where,
            Collection<String> pendingCancellations) {
        int count = 0;
        if (constraint.shouldNotRequireNetwork()) {
            where.args[count++] = Long.toString(constraint.getNowInNs());
        }
        if (constraint.shouldNotRequireUnmeteredNetwork()) {
            where.args[count++] = Long.toString(constraint.getNowInNs());
        }
        if (constraint.getTimeLimit() != null) {
            where.args[count++] = Long.toString(constraint.getTimeLimit());
        }
        if (constraint.getTagConstraint() != null) {
            for (String tag : constraint.getTags()) {
                where.args[count++] = tag;
            }
        }
        for (String group : constraint.getExcludeGroups()) {
            where.args[count++] = group;
        }
        for (String jobId : constraint.getExcludeJobIds()) {
            where.args[count++] = jobId;
        }
        for (String cancelled : pendingCancellations) {
            where.args[count++] = cancelled;
        }
        if (constraint.excludeRunning()) {
            where.args[count++] = sessionId;
        }
        if (count != where.args.length) {
            throw new IllegalStateException("something is wrong with where query cache for "
                    + where.query);
        }
    }

    private Where createWhere(long cacheKey, Constraint constraint,
            Collection<String> pendingCancellations, StringBuilder reusedStringBuilder) {
        reusedStringBuilder.setLength(0);
        int argCount = 0;
        reusedStringBuilder.append("1");
        int networkTimeoutArgIndex = -1;
        int unmeteredTimeoutArgIndex = -1;
        if (constraint.shouldNotRequireNetwork()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.REQUIRES_NETWORK_UNTIL_COLUMN.columnName)
                    .append(" <= ?");
            networkTimeoutArgIndex = argCount;
            argCount++;
        }
        if (constraint.shouldNotRequireUnmeteredNetwork()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.REQUIRES_UNMETERED_NETWORK_UNTIL_COLUMN.columnName)
                    .append(" <= ?");
            unmeteredTimeoutArgIndex = argCount;
            argCount++;
        }
        if (constraint.getTimeLimit() != null) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName)
                    .append(" <= ?");
            argCount++;
        }
        if (constraint.getTagConstraint() != null) {
            if (constraint.getTags().isEmpty()) {
                reusedStringBuilder.append(" AND 0 ");
            } else {
                reusedStringBuilder
                        .append(" AND ")
                        .append(DbOpenHelper.ID_COLUMN.columnName).append(" IN ( SELECT ")
                        .append(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName).append(" FROM ")
                        .append(DbOpenHelper.JOB_TAGS_TABLE_NAME).append(" WHERE ")
                        .append(DbOpenHelper.TAGS_NAME_COLUMN.columnName).append(" IN (");
                SqlHelper.addPlaceholdersInto(reusedStringBuilder,
                        constraint.getTags().size());
                reusedStringBuilder.append(")");
                if (constraint.getTagConstraint() == TagConstraint.ANY) {
                    reusedStringBuilder.append(")");
                } else if (constraint.getTagConstraint() == TagConstraint.ALL) {
                    reusedStringBuilder.append(" GROUP BY (`")
                            .append(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName).append("`)")
                            .append(" HAVING count(*) = ")
                            .append(constraint.getTags().size()).append(")");
                } else {
                    // have this in place in case we change number of constraints
                    throw new IllegalArgumentException("unknown constraint " + constraint);
                }
                argCount += constraint.getTags().size();
            }
        }
        if (!constraint.getExcludeGroups().isEmpty()) {
            reusedStringBuilder
                    .append(" AND (")
                    .append(DbOpenHelper.GROUP_ID_COLUMN.columnName)
                    .append(" IS NULL OR ")
                    .append(DbOpenHelper.GROUP_ID_COLUMN.columnName)
                    .append(" NOT IN(");
            SqlHelper.addPlaceholdersInto(reusedStringBuilder,
                    constraint.getExcludeGroups().size());
            reusedStringBuilder.append("))");
            argCount += constraint.getExcludeGroups().size();
        }
        if (!constraint.getExcludeJobIds().isEmpty()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.ID_COLUMN.columnName)
                    .append(" NOT IN(");
            SqlHelper.addPlaceholdersInto(reusedStringBuilder,
                    constraint.getExcludeJobIds().size());
            reusedStringBuilder.append(")");
            argCount += constraint.getExcludeJobIds().size();
        }
        if (!pendingCancellations.isEmpty()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.ID_COLUMN.columnName)
                    .append(" NOT IN(");
            SqlHelper.addPlaceholdersInto(reusedStringBuilder,
                    pendingCancellations.size());
            reusedStringBuilder.append(")");
            argCount += pendingCancellations.size();
        }
        if (constraint.excludeRunning()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName)
                    .append(" != ?");
            argCount++;
        }
        String[] args = new String[argCount];
        Where where = new Where(cacheKey, reusedStringBuilder.toString(), args);
        where.setNetworkTimeoutArgIndex(networkTimeoutArgIndex);
        where.setUnmeteredNetworkTimeoutArgIndex(unmeteredTimeoutArgIndex);
        return where;
    }

    private boolean isCacheable(Constraint constraint) {
        return constraint.getTags().size() < INT_LIMIT &&
                constraint.getExcludeGroups().size() < INT_LIMIT &&
                constraint.getExcludeJobIds().size() < INT_LIMIT;

    }

    private long cacheKey(Constraint constraint, Collection<String> pendingCancelations) {
        long key;
        //noinspection PointlessBitwiseExpression
        key = (constraint.shouldNotRequireNetwork() ? 1 : 0) << NETWORK
                | (constraint.shouldNotRequireUnmeteredNetwork() ? 1 : 0) << UNMETERED_NETWORK
                | (constraint.getTagConstraint() == null ? 2 : constraint.getTagConstraint().ordinal()) << TAG_TYPE
                | constraint.getTags().size() << TAG_COUNT
                | constraint.getExcludeGroups().size() << GROUP_COUNT
                | constraint.getExcludeJobIds().size() << JOB_COUNT
                | (constraint.excludeRunning() ? 1 : 0) << EXCLUDE_RUNNING
                | (constraint.getTimeLimit() == null ? 1 : 0) << TIME_LIMIT
                | pendingCancelations.size() << PENDING_CANCELLATIONS;
        return key;
    }

}
