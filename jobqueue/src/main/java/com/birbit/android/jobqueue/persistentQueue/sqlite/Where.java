package com.birbit.android.jobqueue.persistentQueue.sqlite;

import com.birbit.android.jobqueue.Params;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

public class Where {
    public final long cacheKey;
    public final String query;
    public final String[] args;

    private SQLiteStatement countReadyStmt;
    private String findJobsQuery;
    private SQLiteStatement nextJobDelayUntilStmt;
    private SQLiteStatement nextJobDelayUntilViaNetworkStmt;
    private String nextJobQuery;
    private int networkTimeoutArgIndex = -1;
    private int unmeteredNetworkTimeoutArgIndex = -1;

    public Where(long cacheKey, String query, String[] args) {
        this.cacheKey = cacheKey;
        this.query = query;
        this.args = args;
    }

    public void setNetworkTimeoutArgIndex(int index) {
        this.networkTimeoutArgIndex = index;
    }

    public void setUnmeteredNetworkTimeoutArgIndex(int unmeteredNetworkTimeoutArgIndex) {
        this.unmeteredNetworkTimeoutArgIndex = unmeteredNetworkTimeoutArgIndex;
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
    public SQLiteStatement nextJobDelayUntilWithNetworkRequirement(SQLiteDatabase database,
            SqlHelper sqlHelper) {
        if (nextJobDelayUntilViaNetworkStmt == null) {
            StringBuilder sb = sqlHelper.reusedStringBuilder;
            sb.setLength(0);
            sb.append("SELECT max(")
                .append(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName);
            if (networkTimeoutArgIndex != -1) {
                sb.append(",")
                        .append(DbOpenHelper.REQUIRES_NETWORK_UNTIL_COLUMN.columnName);
            }
            if (unmeteredNetworkTimeoutArgIndex != -1) {
                sb.append(",")
                        .append(DbOpenHelper.REQUIRES_UNMETERED_NETWORK_UNTIL_COLUMN.columnName);
            }
            sb.append(") FROM ")
                    .append(DbOpenHelper.JOB_HOLDER_TABLE_NAME)
                    .append(" WHERE ")
                    .append(query);
            // below NOT constraints are safe to add because this query will only be accessed if
            // they are set to be excluded.
            if (networkTimeoutArgIndex != -1) {
                sb.append(" AND ")
                        .append(DbOpenHelper.REQUIRES_NETWORK_UNTIL_COLUMN.columnName)
                        .append(" != ").append(Params.FOREVER);
            }
            if (unmeteredNetworkTimeoutArgIndex != -1) {
                sb.append(" AND ")
                        .append(DbOpenHelper.REQUIRES_UNMETERED_NETWORK_UNTIL_COLUMN.columnName)
                        .append(" != ").append(Params.FOREVER);
            }
            sb.append(" ORDER BY 1 ASC").append(" limit 1");
            String selectQuery = sb.toString();
            nextJobDelayUntilViaNetworkStmt = database.compileStatement(selectQuery);
        } else {
            nextJobDelayUntilViaNetworkStmt.clearBindings();
        }
        for (int i = 1; i <= args.length; i ++) {
            nextJobDelayUntilViaNetworkStmt.bindString(i, args[i - 1]);
        }
        if (networkTimeoutArgIndex != -1) {
            nextJobDelayUntilViaNetworkStmt.bindString(networkTimeoutArgIndex + 1,
                    Long.toString(Params.FOREVER));
        }
        if (unmeteredNetworkTimeoutArgIndex != -1) {
            nextJobDelayUntilViaNetworkStmt.bindString(unmeteredNetworkTimeoutArgIndex + 1,
                    Long.toString(Params.FOREVER));
        }

        return nextJobDelayUntilViaNetworkStmt;
    }

    public SQLiteStatement nextJobDelayUntil(SQLiteDatabase database, SqlHelper sqlHelper) {
        if (nextJobDelayUntilStmt == null) {
            String selectQuery = sqlHelper.createSelectOneField(
                    DbOpenHelper.DELAY_UNTIL_NS_COLUMN,
                    query,
                    1,
                    new SqlHelper.Order(DbOpenHelper.DELAY_UNTIL_NS_COLUMN,
                            SqlHelper.Order.Type.ASC)
            );
            nextJobDelayUntilStmt = database.compileStatement(selectQuery);
        } else {
            nextJobDelayUntilStmt.clearBindings();
        }
        for (int i = 1; i <= args.length; i ++) {
            nextJobDelayUntilStmt.bindString(i, args[i - 1]);
        }
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
        if (nextJobDelayUntilViaNetworkStmt != null) {
            nextJobDelayUntilViaNetworkStmt.close();
            nextJobDelayUntilViaNetworkStmt = null;
        }
    }
}
