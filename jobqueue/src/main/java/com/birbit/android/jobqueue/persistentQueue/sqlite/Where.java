package com.birbit.android.jobqueue.persistentQueue.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.birbit.android.jobqueue.Params;

public class Where {
    public final long cacheKey;
    public final String query;
    public final String[] args;

    private SQLiteStatement countReadyStmt;
    private String findJobsQuery;
    private SQLiteStatement nextJobDelayUntilStmt;
    private String nextJobQuery;
    static final String NEVER = Long.toString(Params.NEVER);
    static final String FOREVER = Long.toString(Params.FOREVER);

    public Where(long cacheKey, String query, String[] args) {
        this.cacheKey = cacheKey;
        this.query = query;
        this.args = args;
    }

    public SQLiteStatement countReady(SQLiteDatabase database, StringBuilder stringBuilder) {
        if (countReadyStmt == null) {
            stringBuilder.setLength(0);
            stringBuilder.append("SELECT SUM(case WHEN ")
                    .append(DbOpenHelper.GROUP_ID_COLUMN.columnName)
                    .append(" is null then group_cnt else 1 end) from (")
                        .append("SELECT count(*) group_cnt, ")
                        .append(DbOpenHelper.GROUP_ID_COLUMN.columnName)
                        .append(" FROM ")
                        .append(DbOpenHelper.JOB_HOLDER_TABLE_NAME)
                        .append(" WHERE ")
                        .append(query)
                        .append(" GROUP BY ")
                        .append(DbOpenHelper.GROUP_ID_COLUMN.columnName)
                    .append(")");
            countReadyStmt = database.compileStatement(stringBuilder.toString());
        } else {
            countReadyStmt.clearBindings();
        }
        for (int i = 1; i <= args.length; i ++) {
            countReadyStmt.bindString(i, args[i - 1]);
        }
        return countReadyStmt;
    }

    public SQLiteStatement nextJobDelayUntil(SQLiteDatabase database, SqlHelper sqlHelper) {
        if (nextJobDelayUntilStmt == null) {
            // cannot use MIN because it always returns a value
            String deadlineQuery = sqlHelper.createSelectOneField(
                    DbOpenHelper.DEADLINE_COLUMN.columnName,
                    query,
                    null);
            String delayQuery = sqlHelper.createSelectOneField(
                    DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName,
                    query,
                    null);
            StringBuilder sb = sqlHelper.reusedStringBuilder;
            sb.setLength(0);
            sb.append("SELECT * FROM (")
                    .append(deadlineQuery)
                    .append(" ORDER BY 1 ASC LIMIT 1")
                    .append(") UNION SELECT * FROM (")
                    .append(delayQuery)
                    .append(" ORDER BY 1 ASC LIMIT 1")
                    .append(") ORDER BY 1 ASC LIMIT 1");
            String selectQuery = sb.toString();
            nextJobDelayUntilStmt = database.compileStatement(selectQuery);
        } else {
            nextJobDelayUntilStmt.clearBindings();
        }
        for (int i = 1; i <= args.length; i ++) {
            nextJobDelayUntilStmt.bindString(i, args[i - 1]);
            nextJobDelayUntilStmt.bindString(i + args.length, args[i - 1]);
        }
        nextJobDelayUntilStmt.bindString(WhereQueryCache.DEADLINE_COLUMN_INDEX, FOREVER);
        nextJobDelayUntilStmt.bindString(WhereQueryCache.DEADLINE_COLUMN_INDEX + args.length, NEVER);

        return nextJobDelayUntilStmt;
    }

    public String nextJob(SqlHelper sqlHelper) {
        if (nextJobQuery == null) {
            nextJobQuery = sqlHelper.createSelect(
                    query,
                    1,
                    new SqlHelper.Order(DbOpenHelper.PRIORITY_COLUMN,
                            SqlHelper.Order.Type.DESC),
                    new SqlHelper.Order(DbOpenHelper.CREATED_NS_COLUMN,
                            SqlHelper.Order.Type.ASC),
                    new SqlHelper.Order(DbOpenHelper.INSERTION_ORDER_COLUMN,
                            SqlHelper.Order.Type.ASC)
            );
        }
        return nextJobQuery;
    }

    public String findJobs(SqlHelper sqlHelper) {
        if (findJobsQuery == null) {
            findJobsQuery = sqlHelper.createSelect(query, null);
        }
        return findJobsQuery;
    }

    public void destroy() {
        if (countReadyStmt != null) {
            countReadyStmt.close();
            countReadyStmt = null;
        }
        if (nextJobDelayUntilStmt != null) {
            nextJobDelayUntilStmt.close();
            nextJobDelayUntilStmt = null;
        }
    }
}
