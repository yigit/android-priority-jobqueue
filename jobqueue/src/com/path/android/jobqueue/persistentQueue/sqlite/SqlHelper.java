package com.path.android.jobqueue.persistentQueue.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import com.path.android.jobqueue.log.JqLog;

/**
 * Helper class for {@link SqliteJobQueue} to generate sql queries and statements.
 */
public class SqlHelper {

    /**package**/ String FIND_BY_ID_QUERY;

    private SQLiteStatement insertStatement;
    private SQLiteStatement insertOrReplaceStatement;
    private SQLiteStatement deleteStatement;
    private SQLiteStatement onJobFetchedForRunningStatement;
    private SQLiteStatement countStatement;
    private SQLiteStatement nextJobDelayedUntilWithNetworkStatement;
    private SQLiteStatement nextJobDelayedUntilWithoutNetworkStatement;


    final SQLiteDatabase db;
    final String tableName;
    final String primaryKeyColumnName;
    final int columnCount;
    final long sessionId;

    public SqlHelper(SQLiteDatabase db, String tableName, String primaryKeyColumnName, int columnCount, long sessionId) {
        this.db = db;
        this.tableName = tableName;
        this.columnCount = columnCount;
        this.primaryKeyColumnName = primaryKeyColumnName;
        this.sessionId = sessionId;
        FIND_BY_ID_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName + " = ?";
    }

    public static String create(String tableName, Property primaryKey, Property... properties) {
        StringBuilder builder = new StringBuilder("CREATE TABLE ");
        builder.append(tableName).append(" (");
        builder.append(primaryKey.columnName).append(" ");
        builder.append(primaryKey.type);
        builder.append("  primary key autoincrement ");
        for (Property property : properties) {
            builder.append(", `").append(property.columnName).append("` ").append(property.type);
        }
        builder.append(" );");
        JqLog.d(builder.toString());
        return builder.toString();
    }

    public static String drop(String tableName) {
        return "DROP TABLE IF EXISTS " + tableName;
    }

    public SQLiteStatement getInsertStatement() {
        if (insertStatement == null) {
            StringBuilder builder = new StringBuilder("INSERT INTO ").append(tableName);
            builder.append(" VALUES (");
            for (int i = 0; i < columnCount; i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append("?");
            }
            builder.append(")");
            insertStatement = db.compileStatement(builder.toString());
        }
        return insertStatement;
    }

    public SQLiteStatement getCountStatement() {
        if (countStatement == null) {
            countStatement = db.compileStatement("SELECT COUNT(*) FROM " + tableName + " WHERE " +
                    DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != ?");
        }
        return countStatement;
    }

    public SQLiteStatement getInsertOrReplaceStatement() {
        if (insertOrReplaceStatement == null) {
            StringBuilder builder = new StringBuilder("INSERT OR REPLACE INTO ").append(tableName);
            builder.append(" VALUES (");
            for (int i = 0; i < columnCount; i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append("?");
            }
            builder.append(")");
            insertOrReplaceStatement = db.compileStatement(builder.toString());
        }
        return insertOrReplaceStatement;
    }

    public SQLiteStatement getDeleteStatement() {
        if (deleteStatement == null) {
            deleteStatement = db.compileStatement("DELETE FROM " + tableName + " WHERE " + primaryKeyColumnName + " = ?");
        }
        return deleteStatement;
    }

    public SQLiteStatement getOnJobFetchedForRunningStatement() {
        if (onJobFetchedForRunningStatement == null) {
            String sql = "UPDATE " + tableName + " SET "
                    + DbOpenHelper.RUN_COUNT_COLUMN.columnName + " = ? , "
                    + DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " = ? "
                    + " WHERE " + primaryKeyColumnName + " = ? ";
            onJobFetchedForRunningStatement = db.compileStatement(sql);
        }
        return onJobFetchedForRunningStatement;
    }

    public SQLiteStatement getNextJobDelayedUntilWithNetworkStatement() {
        if(nextJobDelayedUntilWithNetworkStatement == null) {
            String sql = "SELECT " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName
                    + " FROM " + tableName + " WHERE "
                    + DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != " + sessionId
                    + " ORDER BY " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + " ASC"
                    + " LIMIT 1";
            nextJobDelayedUntilWithNetworkStatement = db.compileStatement(sql);
        }
        return nextJobDelayedUntilWithNetworkStatement;
    }

    public SQLiteStatement getNextJobDelayedUntilWithoutNetworkStatement() {
        if(nextJobDelayedUntilWithoutNetworkStatement == null) {
            String sql = "SELECT " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName
                    + " FROM " + tableName + " WHERE "
                    + DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != " + sessionId
                    + " AND " + DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnName + " != 1"
                    + " ORDER BY " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + " ASC"
                    + " LIMIT 1";
            nextJobDelayedUntilWithoutNetworkStatement = db.compileStatement(sql);
        }
        return nextJobDelayedUntilWithoutNetworkStatement;
    }

    public String createSelect(String where, Integer limit, Order... orders) {
        StringBuilder builder = new StringBuilder("SELECT * FROM ");
        builder.append(tableName);
        if (where != null) {
            builder.append(" WHERE ").append(where);
        }
        boolean first = true;
        for (Order order : orders) {
            if (first) {
                builder.append(" ORDER BY ");
            } else {
                builder.append(",");
            }
            first = false;
            builder.append(order.property.columnName).append(" ").append(order.type);
        }
        if (limit != null) {
            builder.append(" LIMIT ").append(limit);
        }
        return builder.toString();
    }

    public void truncate() {
        db.execSQL("DELETE FROM " + DbOpenHelper.JOB_HOLDER_TABLE_NAME);
        vacuum();
    }

    public void vacuum() {
        db.execSQL("VACUUM");
    }

    public void resetDelayTimesTo(long newDelayTime) {
        db.execSQL("UPDATE " + DbOpenHelper.JOB_HOLDER_TABLE_NAME + " SET " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + "=?"
            , new Object[]{newDelayTime});
    }

    public static class Property {
        /*package*/ final String columnName;
        /*package*/ final String type;
        public final int columnIndex;

        public Property(String columnName, String type, int columnIndex) {
            this.columnName = columnName;
            this.type = type;
            this.columnIndex = columnIndex;
        }
    }

    public static class Order {
        final Property property;
        final Type type;

        public Order(Property property, Type type) {
            this.property = property;
            this.type = type;
        }

        public static enum Type {
            ASC,
            DESC
        }

    }
}
