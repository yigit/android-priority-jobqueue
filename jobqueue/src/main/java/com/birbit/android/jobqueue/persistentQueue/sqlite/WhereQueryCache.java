package com.birbit.android.jobqueue.persistentQueue.sqlite;

import androidx.collection.LruCache;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.TagConstraint;

/**
 * Internal class to cache sql queries and statements.
 */
class WhereQueryCache {
    private static final int INT_SIZE = 6;
    private static final int BOOL_SIZE = 1;
    private static final int TAG_TYPE = 0;
    private static final int TAG_COUNT = TAG_TYPE + BOOL_SIZE + BOOL_SIZE;
    private static final int GROUP_COUNT = TAG_COUNT + INT_SIZE;
    private static final int JOB_COUNT = GROUP_COUNT + INT_SIZE;
    private static final int EXCLUDE_RUNNING = JOB_COUNT + INT_SIZE;
    private static final int TIME_LIMIT = EXCLUDE_RUNNING + BOOL_SIZE;
    private static final int INT_LIMIT = 1 << INT_SIZE;

    static final int DEADLINE_COLUMN_INDEX = 1;

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

    public Where build(Constraint constraint, StringBuilder stringBuilder) {
        final boolean cacheable = isCacheable(constraint);
        final long cacheKey = cacheKey(constraint);
        Where where = cacheable ? queryCache.get(cacheKey) : null;
        if (where == null) {
            // build it
            where = createWhere(cacheKey, constraint, stringBuilder);
            if (cacheable) {
                queryCache.put(cacheKey, where);
            }
        }
        fillWhere(constraint, where);
        return where;
    }

    private void fillWhere(Constraint constraint, Where where) {
        int count = 0;
        where.args[count++] = Long.toString(constraint.getNowInNs());
        where.args[count++] = Integer.toString(constraint.getMaxNetworkType());
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
        if (constraint.excludeRunning()) {
            where.args[count++] = sessionId;
        }
        if (count != where.args.length) {
            throw new IllegalStateException("something is wrong with where query cache for "
                    + where.query);
        }
    }

    private Where createWhere(long cacheKey, Constraint constraint,
            StringBuilder reusedStringBuilder) {
        reusedStringBuilder.setLength(0);
        int argCount = 0;

        reusedStringBuilder
                .append("( (")
                // deadline 4ever check is necessary to filter these from next job queries
                .append(DbOpenHelper.DEADLINE_COLUMN.columnName)
                .append(" != ").append(Where.FOREVER)
                .append(" AND ")
                .append(DbOpenHelper.DEADLINE_COLUMN.columnName)
                .append(" <= ?) OR ");
        argCount ++;

        reusedStringBuilder
                .append(DbOpenHelper.REQUIRED_NETWORK_TYPE_COLUMN.columnName)
                .append(" <= ?)");

        reusedStringBuilder.append(" AND (")
                .append(DbOpenHelper.CANCELLED_COLUMN.columnName)
                .append(" IS NULL OR ")
                .append(DbOpenHelper.CANCELLED_COLUMN.columnName)
                .append( " != 1)");

        argCount++;
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
        if (constraint.excludeRunning()) {
            reusedStringBuilder
                    .append(" AND ")
                    .append(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName)
                    .append(" != ?");
            argCount++;
        }
        String[] args = new String[argCount];
        //noinspection UnnecessaryLocalVariable
        Where where = new Where(cacheKey, reusedStringBuilder.toString(), args);
        return where;
    }

    private boolean isCacheable(Constraint constraint) {
        return constraint.getTags().size() < INT_LIMIT &&
                constraint.getExcludeGroups().size() < INT_LIMIT &&
                constraint.getExcludeJobIds().size() < INT_LIMIT;

    }

    private long cacheKey(Constraint constraint) {
        long key;
        //noinspection PointlessBitwiseExpression
        key = (constraint.getTagConstraint() == null ? 2 : constraint.getTagConstraint().ordinal()) << TAG_TYPE
                | constraint.getTags().size() << TAG_COUNT
                | constraint.getExcludeGroups().size() << GROUP_COUNT
                | constraint.getExcludeJobIds().size() << JOB_COUNT
                | (constraint.excludeRunning() ? 1 : 0) << EXCLUDE_RUNNING
                | (constraint.getTimeLimit() == null ? 1 : 0) << TIME_LIMIT;
        return key;
    }

}
