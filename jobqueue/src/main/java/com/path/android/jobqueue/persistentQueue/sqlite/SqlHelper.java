package com.path.android.jobqueue.persistentQueue.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.log.JqLog;

import java.util.Collection;

/**
 * Helper class for {@link SqliteJobQueue} to generate sql queries and statements.
 */
public class SqlHelper {

    /**package**/ String FIND_BY_ID_QUERY;
    /**package**/ String FIND_BY_TAG_QUERY;

    private SQLiteStatement insertStatement;
    private SQLiteStatement insertTagsStatement;
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
    final String tagsTableName;
    final int tagsColumnCount;
    final long sessionId;

    public SqlHelper(SQLiteDatabase db, String tableName, String primaryKeyColumnName,
            int columnCount, String tagsTableName, int tagsColumnCount, long sessionId) {
        this.db = db;
        this.tableName = tableName;
        this.columnCount = columnCount;
        this.primaryKeyColumnName = primaryKeyColumnName;
        this.sessionId = sessionId;
        this.tagsColumnCount = tagsColumnCount;
        this.tagsTableName = tagsTableName;
        FIND_BY_ID_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName + " = ?";
        FIND_BY_TAG_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName
                + " IN ( SELECT " + DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName + " FROM " + tagsTableName
                + " WHERE " + DbOpenHelper.TAGS_NAME_COLUMN.columnName + " = ?)";
    }

    public static String create(String tableName, Property primaryKey, Property... properties) {
        StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        builder.append(tableName).append(" (");
        builder.append(primaryKey.columnName).append(" ");
        builder.append(primaryKey.type);
        builder.append("  primary key autoincrement ");
        for (Property property : properties) {
            builder.append(", `").append(property.columnName).append("` ").append(property.type);
        }
        for (Property property : properties) {
            if (property.foreignKey != null) {
                ForeignKey key = property.foreignKey;
                builder.append(", FOREIGN KEY(`").append(property.columnName)
                        .append("`) REFERENCES ").append(key.targetTable).append("(`")
                        .append(key.targetFieldName).append("`) ON DELETE CASCADE");
            }
        }
        builder.append(" );");
        JqLog.d(builder.toString());
        return builder.toString();
    }

    public String createFindByTagsQuery(TagConstraint constraint, int numberOfExcludeIds,
            int numberOfTags) {
        StringBuilder query = new StringBuilder();
        String placeHolders = createPlaceholders(numberOfTags);
        query.append("SELECT * FROM ").append(tableName).append(" WHERE ");
        query.append(DbOpenHelper.ID_COLUMN.columnName).append(" IN ( SELECT ")
                .append(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName).append(" FROM ")
                .append(tagsTableName).append(" WHERE ")
                .append(DbOpenHelper.TAGS_NAME_COLUMN.columnName).append(" IN (")
                .append(placeHolders).append(")");
        if (constraint == TagConstraint.ANY) {
            query.append(")");
        } else if (constraint == TagConstraint.ALL) {
            query.append(" GROUP BY (`")
                    .append(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName).append("`)")
                    .append(" HAVING count(*) = ")
                    .append(numberOfTags).append(")");
        } else {
            // have this in place in case we change number of constraints
            throw new IllegalArgumentException("unknown constraint " + constraint);
        }
        if (numberOfExcludeIds > 0) {
            String idPlaceHolders = createPlaceholders(numberOfExcludeIds);
            query.append(" AND ").append(DbOpenHelper.ID_COLUMN.columnName)
                    .append(" NOT IN(").append(idPlaceHolders).append(")");
        }

        return query.toString();
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

    public SQLiteStatement getInsertTagsStatement() {
        if (insertTagsStatement == null) {
            StringBuilder builder = new StringBuilder("INSERT INTO ").append(DbOpenHelper.JOB_TAGS_TABLE_NAME);
            builder.append(" VALUES (");
            for (int i = 0; i < tagsColumnCount; i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append("?");
            }
            builder.append(")");
            insertTagsStatement = db.compileStatement(builder.toString());
        }
        return insertTagsStatement;
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

    public String createNextJobDelayUntilQuery(boolean hasNetwork, Collection<String> excludeGroups) {
        String sql = "SELECT " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName
                + " FROM " + tableName + " WHERE "
                + DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != " + sessionId;
        if (!hasNetwork) {
            sql += " AND " + DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnName + " != 1";
        }
        if(excludeGroups != null && excludeGroups.size() > 0) {
            sql +=  " AND (" + DbOpenHelper.GROUP_ID_COLUMN.columnName + " IS NULL OR " +
                    DbOpenHelper.GROUP_ID_COLUMN.columnName +
                    " NOT IN('" + joinStrings("','", excludeGroups) + "'))";
        }
        sql += " ORDER BY " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + " ASC"
                + " LIMIT 1";
        return sql;
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

    /**
     * returns a placeholder string that contains <code>count</code> placeholders. e.g. ?,?,? for
     * 3.
     * @param count Number of placeholders to add.
     */
    private static String createPlaceholders(int count) {
        if (count == 0) {
            throw new IllegalArgumentException("cannot create placeholders for 0 items");
        }
        final StringBuilder builder = new StringBuilder("?");
        for (int i = 1; i < count; i ++) {
            builder.append(",?");
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
        db.execSQL("UPDATE " + DbOpenHelper.JOB_HOLDER_TABLE_NAME + " SET "
                + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + "=?"
            , new Object[]{newDelayTime});
    }

    // TODO we are using this to merge groups but not escaping :/
    public static String joinStrings(String glue, Collection<String> strings) {
        StringBuilder builder = new StringBuilder();
        for(String str : strings) {
            if(builder.length() != 0) {
                builder.append(glue);
            }
            builder.append(str);
        }
        return builder.toString();
    }

    public static class Property {
        /*package*/ final String columnName;
        /*package*/ final String type;
        public final int columnIndex;
        public final ForeignKey foreignKey;

        public Property(String columnName, String type, int columnIndex) {
            this(columnName, type, columnIndex, null);
        }

        public Property(String columnName, String type, int columnIndex, ForeignKey foreignKey) {
            this.columnName = columnName;
            this.type = type;
            this.columnIndex = columnIndex;
            this.foreignKey = foreignKey;
        }
    }

    public static class ForeignKey {
        final String targetTable;
        final String targetFieldName;

        public ForeignKey(String targetTable, String targetFieldName) {
            this.targetTable = targetTable;
            this.targetFieldName = targetFieldName;
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
