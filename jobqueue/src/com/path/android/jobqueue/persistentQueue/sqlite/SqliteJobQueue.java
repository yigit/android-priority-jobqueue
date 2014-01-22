package com.path.android.jobqueue.persistentQueue.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.log.JqLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Collection;

/**
 * Persistent Job Queue that keeps its data in an sqlite database.
 */
public class SqliteJobQueue implements JobQueue {
    DbOpenHelper dbOpenHelper;
    private final long sessionId;
    SQLiteDatabase db;
    SqlHelper sqlHelper;
    JobSerializer jobSerializer;
    QueryCache readyJobsQueryCache;
    QueryCache nextJobsQueryCache;

    /**
     * @param context application context
     * @param sessionId session id should match {@link JobManager}
     * @param id uses this value to construct database name {@code "db_" + id}
     */
    public SqliteJobQueue(Context context, long sessionId, String id, JobSerializer jobSerializer) {
        this.sessionId = sessionId;
        dbOpenHelper = new DbOpenHelper(context, "db_" + id);
        db = dbOpenHelper.getWritableDatabase();
        sqlHelper = new SqlHelper(db, DbOpenHelper.JOB_HOLDER_TABLE_NAME, DbOpenHelper.ID_COLUMN.columnName, DbOpenHelper.COLUMN_COUNT, sessionId);
        this.jobSerializer = jobSerializer;
        readyJobsQueryCache = new QueryCache();
        nextJobsQueryCache = new QueryCache();
        sqlHelper.resetDelayTimesTo(JobManager.NOT_DELAYED_JOB_DELAY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long insert(JobHolder jobHolder) {
        SQLiteStatement stmt = sqlHelper.getInsertStatement();
        long id;
        synchronized (stmt) {
            stmt.clearBindings();
            bindValues(stmt, jobHolder);
            id = stmt.executeInsert();
        }
        jobHolder.setId(id);
        return id;
    }

    private void bindValues(SQLiteStatement stmt, JobHolder jobHolder) {
        if (jobHolder.getId() != null) {
            stmt.bindLong(DbOpenHelper.ID_COLUMN.columnIndex + 1, jobHolder.getId());
        }
        stmt.bindLong(DbOpenHelper.PRIORITY_COLUMN.columnIndex + 1, jobHolder.getPriority());
        if(jobHolder.getGroupId() != null) {
            stmt.bindString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex + 1, jobHolder.getGroupId());
        }
        stmt.bindLong(DbOpenHelper.RUN_COUNT_COLUMN.columnIndex + 1, jobHolder.getRunCount());
        byte[] baseJob = getSerializeBaseJob(jobHolder);
        if (baseJob != null) {
            stmt.bindBlob(DbOpenHelper.BASE_JOB_COLUMN.columnIndex + 1, baseJob);
        }
        stmt.bindLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex + 1, jobHolder.getCreatedNs());
        stmt.bindLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex + 1, jobHolder.getDelayUntilNs());
        stmt.bindLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex + 1, jobHolder.getRunningSessionId());
        stmt.bindLong(DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnIndex + 1, jobHolder.requiresNetwork() ? 1L : 0L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        if (jobHolder.getId() == null) {
            return insert(jobHolder);
        }
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        SQLiteStatement stmt = sqlHelper.getInsertOrReplaceStatement();
        long id;
        synchronized (stmt) {
            stmt.clearBindings();
            bindValues(stmt, jobHolder);
            id = stmt.executeInsert();
        }
        jobHolder.setId(id);
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(JobHolder jobHolder) {
        if (jobHolder.getId() == null) {
            JqLog.e("called remove with null job id.");
            return;
        }
        delete(jobHolder.getId());
    }

    private void delete(Long id) {
        SQLiteStatement stmt = sqlHelper.getDeleteStatement();
        synchronized (stmt) {
            stmt.clearBindings();
            stmt.bindLong(1, id);
            stmt.execute();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int count() {
        SQLiteStatement stmt = sqlHelper.getCountStatement();
        synchronized (stmt) {
            stmt.clearBindings();
            stmt.bindLong(1, sessionId);
            return (int) stmt.simpleQueryForLong();
        }
    }

    @Override
    public int countReadyJobs(boolean hasNetwork, Collection<String> excludeGroups) {
        String sql = readyJobsQueryCache.get(hasNetwork, excludeGroups);
        if(sql == null) {
            String where = createReadyJobWhereSql(hasNetwork, excludeGroups, true);
            String subSelect = "SELECT count(*) group_cnt, " + DbOpenHelper.GROUP_ID_COLUMN.columnName
                    + " FROM " + DbOpenHelper.JOB_HOLDER_TABLE_NAME
                    + " WHERE " + where;
            sql = "SELECT SUM(case WHEN " + DbOpenHelper.GROUP_ID_COLUMN.columnName
                    + " is null then group_cnt else 1 end) from (" + subSelect + ")";
            readyJobsQueryCache.set(sql, hasNetwork, excludeGroups);
        }
        Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(sessionId), Long.toString(System.nanoTime())});
        try {
            if(!cursor.moveToNext()) {
                return 0;
            }
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder findJobById(long id) {
        Cursor cursor = db.rawQuery(sqlHelper.FIND_BY_ID_QUERY, new String[]{Long.toString(id)});
        try {
            if(!cursor.moveToFirst()) {
                return null;
            }
            return createJobHolderFromCursor(cursor);
        } catch (InvalidBaseJobException e) {
            JqLog.e(e, "invalid job on findJobById");
            return null;
        } finally {
            cursor.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount(boolean hasNetwork, Collection<String> excludeGroups) {
        //we can even keep these prepared but not sure the cost of them in db layer
        String selectQuery = nextJobsQueryCache.get(hasNetwork, excludeGroups);
        if(selectQuery == null) {
            String where = createReadyJobWhereSql(hasNetwork, excludeGroups, false);
            selectQuery = sqlHelper.createSelect(
                    where,
                    1,
                    new SqlHelper.Order(DbOpenHelper.PRIORITY_COLUMN, SqlHelper.Order.Type.DESC),
                    new SqlHelper.Order(DbOpenHelper.CREATED_NS_COLUMN, SqlHelper.Order.Type.ASC),
                    new SqlHelper.Order(DbOpenHelper.ID_COLUMN, SqlHelper.Order.Type.ASC)
            );
            nextJobsQueryCache.set(selectQuery, hasNetwork, excludeGroups);
        }
        Cursor cursor = db.rawQuery(selectQuery, new String[]{Long.toString(sessionId),Long.toString(System.nanoTime())});
        try {
            if (!cursor.moveToNext()) {
                return null;
            }
            JobHolder holder = createJobHolderFromCursor(cursor);
            onJobFetchedForRunning(holder);
            return holder;
        } catch (InvalidBaseJobException e) {
            //delete
            Long jobId = cursor.getLong(0);
            delete(jobId);
            return nextJobAndIncRunCount(true, null);
        } finally {
            cursor.close();
        }
    }

    private String createReadyJobWhereSql(boolean hasNetwork, Collection<String> excludeGroups, boolean groupByRunningGroup) {
        String where = DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != ? "
                + " AND " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + " <= ? ";
        if(hasNetwork == false) {
            where += " AND " + DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnName + " != 1 ";
        }
        String groupConstraint = null;
        if(excludeGroups != null && excludeGroups.size() > 0) {
            groupConstraint = DbOpenHelper.GROUP_ID_COLUMN.columnName + " IS NULL OR " +
                    DbOpenHelper.GROUP_ID_COLUMN.columnName + " NOT IN('" + joinStrings("','", excludeGroups) + "')";
        }
        if(groupByRunningGroup) {
            where += " GROUP BY " + DbOpenHelper.GROUP_ID_COLUMN.columnName;
            if(groupConstraint != null) {
                where += " HAVING " + groupConstraint;
            }
        } else if(groupConstraint != null) {
            where += " AND ( " + groupConstraint + " )";
        }
        return where;
    }

    private static String joinStrings(String glue, Collection<String> strings) {
        StringBuilder builder = new StringBuilder();
        for(String str : strings) {
            if(builder.length() != 0) {
                builder.append(glue);
            }
            builder.append(str);
        }
        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextJobDelayUntilNs(boolean hasNetwork) {
        SQLiteStatement stmt =
                hasNetwork ? sqlHelper.getNextJobDelayedUntilWithNetworkStatement()
                : sqlHelper.getNextJobDelayedUntilWithoutNetworkStatement();
        synchronized (stmt) {
            try {
                stmt.clearBindings();
                return stmt.simpleQueryForLong();
            } catch (SQLiteDoneException e){
                return null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        sqlHelper.truncate();
        readyJobsQueryCache.clear();
        nextJobsQueryCache.clear();
    }

    private void onJobFetchedForRunning(JobHolder jobHolder) {
        SQLiteStatement stmt = sqlHelper.getOnJobFetchedForRunningStatement();
        jobHolder.setRunCount(jobHolder.getRunCount() + 1);
        jobHolder.setRunningSessionId(sessionId);
        synchronized (stmt) {
            stmt.clearBindings();
            stmt.bindLong(1, jobHolder.getRunCount());
            stmt.bindLong(2, sessionId);
            stmt.bindLong(3, jobHolder.getId());
            stmt.execute();
        }
    }

    private JobHolder createJobHolderFromCursor(Cursor cursor) throws InvalidBaseJobException {
        BaseJob job = safeDeserialize(cursor.getBlob(DbOpenHelper.BASE_JOB_COLUMN.columnIndex));
        if (job == null) {
            throw new InvalidBaseJobException();
        }
        return new JobHolder(
                cursor.getLong(DbOpenHelper.ID_COLUMN.columnIndex),
                cursor.getInt(DbOpenHelper.PRIORITY_COLUMN.columnIndex),
                cursor.getString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex),
                cursor.getInt(DbOpenHelper.RUN_COUNT_COLUMN.columnIndex),
                job,
                cursor.getLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex),
                cursor.getLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex),
                cursor.getLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex)
        );

    }

    private BaseJob safeDeserialize(byte[] bytes) {
        try {
            return jobSerializer.deserialize(bytes);
        } catch (Throwable t) {
            JqLog.e(t, "error while deserializing job");
        }
        return null;
    }

    private byte[] getSerializeBaseJob(JobHolder jobHolder) {
        return safeSerialize(jobHolder.getBaseJob());
    }

    private byte[] safeSerialize(Object object) {
        try {
            return jobSerializer.serialize(object);
        } catch (Throwable t) {
            JqLog.e(t, "error while serializing object %s", object.getClass().getSimpleName());
        }
        return null;
    }

    private static class InvalidBaseJobException extends Exception {

    }

    public static class JavaSerializer implements JobSerializer {

        @Override
        public byte[] serialize(Object object) throws IOException {
            if (object == null) {
                return null;
            }
            ByteArrayOutputStream bos = null;
            try {
                ObjectOutput out = null;
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);
                out.writeObject(object);
                // Get the bytes of the serialized object
                return bos.toByteArray();
            } finally {
                if (bos != null) {
                    bos.close();
                }
            }
        }

        @Override
        public <T extends BaseJob> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ObjectInputStream in = null;
            try {
                in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                return (T) in.readObject();
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }
    }

    public static interface JobSerializer {
        public byte[] serialize(Object object) throws IOException;
        public <T extends BaseJob> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException;
    }
}
