package com.birbit.android.jobqueue.persistentQueue.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.birbit.android.jobqueue.log.JqLog;

/**
 * Helper class for {@link SqliteJobQueue} to generate sql queries and statements.
 */
public class SqlHelper {

    /**
     * package
     **/
    String FIND_BY_ID_QUERY;
    /**
     * package
     **/
    String FIND_BY_TAG_QUERY;
    /**
     * package
     **/
    String FIND_BY_DEPENDEE_TAG_QUERY;
    /**
     * package
     **/
    String LOAD_ALL_IDS_QUERY;
    /**
     * package
     **/
    String LOAD_TAGS_QUERY;
    /**
     * package
     **/
    String LOAD_DEPENDENT_TAGS_QUERY;
    /**
     * package
     **/
    String RE_ENABLE_PENDING_CANCELLATIONS_QUERY;

    private SQLiteStatement insertStatement;
    private SQLiteStatement insertTagsStatement;
    private SQLiteStatement insertDependeeTagsStatement;
    private SQLiteStatement insertOrReplaceStatement;
    private SQLiteStatement deleteStatement;
    private SQLiteStatement deleteJobTagsStatement;
    private SQLiteStatement deleteDependeeJobTagsStatement;
    private SQLiteStatement onJobFetchedForRunningStatement;
    private SQLiteStatement countStatement;
    private SQLiteStatement markAsCancelledStatement;
    private SQLiteStatement markAsScheduledStatement;
    final StringBuilder reusedStringBuilder = new StringBuilder();


    final SQLiteDatabase db;
    final String tableName;
    final String primaryKeyColumnName;
    final int columnCount;
    final String tagsTableName;
    final int tagsColumnCount;
    final String dependeeTagsTableName;
    final int dependeeTagsColumnCount;
    final long sessionId;

    public SqlHelper(SQLiteDatabase db, String tableName, String primaryKeyColumnName,
                     int columnCount, String tagsTableName, int tagsColumnCount,
                     String dependeeTagsTableName, int dependeeTagsColumnCount, long sessionId) {
        this.db = db;
        this.tableName = tableName;
        this.columnCount = columnCount;
        this.primaryKeyColumnName = primaryKeyColumnName;
        this.sessionId = sessionId;
        this.tagsColumnCount = tagsColumnCount;
        this.tagsTableName = tagsTableName;
        this.dependeeTagsTableName = dependeeTagsTableName;
        this.dependeeTagsColumnCount = dependeeTagsColumnCount;
        FIND_BY_ID_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName + " = ?";
        FIND_BY_TAG_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName
                + " IN ( SELECT " + DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName + " FROM " + tagsTableName
                + " WHERE " + DbOpenHelper.TAGS_NAME_COLUMN.columnName + " = ?)";
        FIND_BY_DEPENDEE_TAG_QUERY = "SELECT * FROM " + tableName + " WHERE " + DbOpenHelper.ID_COLUMN.columnName
                + " IN ( SELECT " + DbOpenHelper.DEPENDEE_TAGS_JOB_ID_COLUMN.columnName + " FROM " + dependeeTagsTableName
                + " WHERE " + DbOpenHelper.DEPENDEE_TAGS_NAME_COLUMN.columnName + " = ?)";
        LOAD_ALL_IDS_QUERY = "SELECT " + DbOpenHelper.ID_COLUMN.columnName + " FROM " + tableName;
        LOAD_TAGS_QUERY = "SELECT " + DbOpenHelper.TAGS_NAME_COLUMN.columnName + " FROM "
                + DbOpenHelper.JOB_TAGS_TABLE_NAME + " WHERE "
                + DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName + " = ?";
        LOAD_DEPENDENT_TAGS_QUERY = "SELECT " + DbOpenHelper.DEPENDEE_TAGS_NAME_COLUMN.columnName + " FROM "
                + DbOpenHelper.JOB_DEPENDEE_TAGS_TABLE_NAME + " WHERE "
                + DbOpenHelper.DEPENDEE_TAGS_JOB_ID_COLUMN.columnName + " = ?";
        RE_ENABLE_PENDING_CANCELLATIONS_QUERY = "UPDATE " + tableName + " SET "
                + DbOpenHelper.CANCELLED_COLUMN.columnName + " = 0";
    }

    public static String create(String tableName, Property primaryKey, Property... properties) {
        StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        builder.append(tableName).append(" (");
        builder.append(primaryKey.columnName).append(" ");
        builder.append(primaryKey.type);
        builder.append("  primary key ");
        for (Property property : properties) {
            builder.append(", `").append(property.columnName).append("` ").append(property.type);
            if (property.unique) {
                builder.append(" UNIQUE");
            }
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

    public static String drop(String tableName) {
        return "DROP TABLE IF EXISTS " + tableName;
    }

    public SQLiteStatement getInsertStatement() {
        if (insertStatement == null) {
            reusedStringBuilder.setLength(0);
            reusedStringBuilder.append("INSERT INTO ").append(tableName);
            reusedStringBuilder.append(" VALUES (");
            for (int i = 0; i < columnCount; i++) {
                if (i != 0) {
                    reusedStringBuilder.append(",");
                }
                reusedStringBuilder.append("?");
            }
            reusedStringBuilder.append(")");
            insertStatement = db.compileStatement(reusedStringBuilder.toString());
        }
        return insertStatement;
    }

    public SQLiteStatement getInsertTagsStatement() {
        if (insertTagsStatement == null) {
            reusedStringBuilder.setLength(0);
            reusedStringBuilder.append("INSERT INTO ")
                    .append(DbOpenHelper.JOB_TAGS_TABLE_NAME);
            reusedStringBuilder.append(" VALUES (");
            for (int i = 0; i < tagsColumnCount; i++) {
                if (i != 0) {
                    reusedStringBuilder.append(",");
                }
                reusedStringBuilder.append("?");
            }
            reusedStringBuilder.append(")");
            insertTagsStatement = db.compileStatement(reusedStringBuilder.toString());
        }
        return insertTagsStatement;
    }

    public SQLiteStatement getInsertDependeeTagsStatement() {
        if (insertDependeeTagsStatement == null) {
            reusedStringBuilder.setLength(0);
            reusedStringBuilder.append("INSERT INTO ")
                    .append(DbOpenHelper.JOB_DEPENDEE_TAGS_TABLE_NAME);
            reusedStringBuilder.append(" VALUES (");
            for (int i = 0; i < tagsColumnCount; i++) {
                if (i != 0) {
                    reusedStringBuilder.append(",");
                }
                reusedStringBuilder.append("?");
            }
            reusedStringBuilder.append(")");
            insertDependeeTagsStatement = db.compileStatement(reusedStringBuilder.toString());
        }
        return insertDependeeTagsStatement;
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
            reusedStringBuilder.setLength(0);
            reusedStringBuilder.append("INSERT OR REPLACE INTO ").append(tableName);
            reusedStringBuilder.append(" VALUES (");
            for (int i = 0; i < columnCount; i++) {
                if (i != 0) {
                    reusedStringBuilder.append(",");
                }
                reusedStringBuilder.append("?");
            }
            reusedStringBuilder.append(")");
            insertOrReplaceStatement = db.compileStatement(reusedStringBuilder.toString());
        }
        return insertOrReplaceStatement;
    }

    public SQLiteStatement getDeleteStatement() {
        if (deleteStatement == null) {
            deleteStatement = db.compileStatement("DELETE FROM " + tableName + " WHERE "
                    + primaryKeyColumnName + " = ?");
        }
        return deleteStatement;
    }

    public SQLiteStatement getDeleteJobTagsStatement() {
        if (deleteJobTagsStatement == null) {
            deleteJobTagsStatement = db.compileStatement("DELETE FROM " + tagsTableName
                    + " WHERE " + DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName + "= ?");
        }
        return deleteJobTagsStatement;
    }

    public SQLiteStatement getDeleteDependeeJobTagsStatement() {
        if (deleteDependeeJobTagsStatement == null) {
            deleteDependeeJobTagsStatement = db.compileStatement("DELETE FROM " + dependeeTagsTableName
                    + " WHERE " + DbOpenHelper.DEPENDEE_TAGS_JOB_ID_COLUMN.columnName + "= ?");
        }
        return deleteDependeeJobTagsStatement;
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

    public SQLiteStatement getMarkAsCancelledStatement() {
        if (markAsCancelledStatement == null) {
            String sql = "UPDATE " + tableName + " SET "
                    + DbOpenHelper.CANCELLED_COLUMN.columnName + " = 1 "
                    + " WHERE " + primaryKeyColumnName + " = ? ";
            markAsCancelledStatement = db.compileStatement(sql);
        }
        return markAsCancelledStatement;
    }

    static void addPlaceholdersInto(StringBuilder stringBuilder, int count) {
        if (count == 0) {
            throw new IllegalArgumentException("cannot create placeholders for 0 items");
        }
        stringBuilder.append("?");
        for (int i = 1; i < count; i++) {
            stringBuilder.append(",?");
        }
    }

    public String createSelect(String where, Integer limit, Order... orders) {
        reusedStringBuilder.setLength(0);
        reusedStringBuilder.append("SELECT * FROM ");
        reusedStringBuilder.append(tableName);
        if (where != null) {
            reusedStringBuilder.append(" WHERE ").append(where);
        }
        boolean first = true;
        for (Order order : orders) {
            if (first) {
                reusedStringBuilder.append(" ORDER BY ");
            } else {
                reusedStringBuilder.append(",");
            }
            first = false;
            reusedStringBuilder.append(order.property.columnName).append(" ").append(order.type);
        }
        if (limit != null) {
            reusedStringBuilder.append(" LIMIT ").append(limit);
        }
        return reusedStringBuilder.toString();
    }

    public SQLiteStatement getMarkAsScheduledStatement() {
        if (markAsScheduledStatement == null) {
            String sql = "UPDATE " + tableName + " SET "
                    + DbOpenHelper.SCHEDULE_REQUESTED_AT_NS.columnName + " = ? "
                    + " WHERE " + primaryKeyColumnName + " = ? ";
            markAsScheduledStatement = db.compileStatement(sql);
        }
        return markAsScheduledStatement;
    }

    public String createSelectOneField(String selectArg, String where, Integer limit,
                                       Order... orders) {
        reusedStringBuilder.setLength(0);

        reusedStringBuilder.append("SELECT ")
                .append(selectArg).append(" FROM ")
                .append(tableName);
        if (where != null) {
            reusedStringBuilder.append(" WHERE ").append(where);
        }
        boolean first = true;
        for (Order order : orders) {
            if (first) {
                reusedStringBuilder.append(" ORDER BY ");
            } else {
                reusedStringBuilder.append(",");
            }
            first = false;
            reusedStringBuilder.append(order.property.columnName).append(" ").append(order.type);
        }
        if (limit != null) {
            reusedStringBuilder.append(" LIMIT ").append(limit);
        }
        return reusedStringBuilder.toString();
    }

    public String getDependentJobsQuery(int tagsLength) {
        reusedStringBuilder.setLength(0);
        reusedStringBuilder
                .append(DbOpenHelper.ID_COLUMN.columnName)
                .append(" IN (SELECT DISTINCT ")
                .append(DbOpenHelper.DEPENDEE_TAGS_JOB_ID_COLUMN.columnName)
                .append(" FROM ")
                .append(DbOpenHelper.JOB_DEPENDEE_TAGS_TABLE_NAME)
                .append(" WHERE ")
                .append(DbOpenHelper.DEPENDEE_TAGS_NAME_COLUMN.columnName)
                .append(" IN (");
        addPlaceholdersInto(reusedStringBuilder, tagsLength);
        reusedStringBuilder.append("))");

        String where = reusedStringBuilder.toString();
        return createSelect(where, null);
    }

    public String getJobsByTagsQuery(int tagsLength) {
        reusedStringBuilder.setLength(0);
        reusedStringBuilder
            .append(DbOpenHelper.ID_COLUMN.columnName)
            .append(" IN (SELECT DISTINCT ")
            .append(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName)
            .append(" FROM ")
            .append(DbOpenHelper.JOB_TAGS_TABLE_NAME)
            .append(" WHERE ")
            .append(DbOpenHelper.TAGS_NAME_COLUMN.columnName)
            .append(" IN (");
        addPlaceholdersInto(reusedStringBuilder, tagsLength);
        reusedStringBuilder.append("))");

        String where = reusedStringBuilder.toString();
        return createSelect(where, null);
    }

    public void truncate() {
        db.execSQL("DELETE FROM " + DbOpenHelper.JOB_HOLDER_TABLE_NAME);
        db.execSQL("DELETE FROM " + DbOpenHelper.JOB_TAGS_TABLE_NAME);
        db.execSQL("DELETE FROM " + DbOpenHelper.JOB_DEPENDEE_TAGS_TABLE_NAME);
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

    public static class Property {
        /*package*/ final String columnName;
        /*package*/ final String type;
        public final int columnIndex;
        public final ForeignKey foreignKey;
        public final boolean unique;

        public Property(String columnName, String type, int columnIndex) {
            this(columnName, type, columnIndex, null, false);
        }

        public Property(String columnName, String type, int columnIndex, ForeignKey foreignKey) {
            this(columnName, type, columnIndex, foreignKey, false);
        }

        public Property(String columnName, String type, int columnIndex, ForeignKey foreignKey,
                        boolean unique) {
            this.columnName = columnName;
            this.type = type;
            this.columnIndex = columnIndex;
            this.foreignKey = foreignKey;
            this.unique = unique;
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

        public enum Type {
            ASC,
            DESC
        }
    }
}
