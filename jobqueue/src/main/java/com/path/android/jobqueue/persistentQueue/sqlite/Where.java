package com.path.android.jobqueue.persistentQueue.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.util.Arrays;

public class Where {
    public final long cacheKey;
    public final String query;
    public final String[] args;

    private SQLiteStatement countReadyStmt;
    private String findJobsQuery;
    private SQLiteStatement nextJobDelayUntilStmt;
    private String nextJobQuery;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Where where = (Where) o;

        if (cacheKey != where.cacheKey) {
            return false;
        }
        if (!query.equals(where.query)) {
            return false;
        }
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(args, where.args);

    }

    public String findJobs(SqlHelper sqlHelper) {
        if (findJobsQuery == null) {
            findJobsQuery = sqlHelper.createSelect(query, null);
        }
        return findJobsQuery;
    }

    @Override
    public int hashCode() {
        int result = (int) (cacheKey ^ (cacheKey >>> 32));
        result = 31 * result + query.hashCode();
        result = 31 * result + Arrays.hashCode(args);
        return result;
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
