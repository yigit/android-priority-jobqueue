package com.path.android.jobqueue.persistentQueue.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import com.path.android.jobqueue.*;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    QueryCache nextJobDelayUntilQueryCache;
    // we keep a list of cancelled jobs in memory not to return them in subsequent find by tag
    // queries. Set is cleaned when item is removed
    Set<String> pendingCancelations = new HashSet<>();
    final Timer timer;

    /**
     * @param context application context
     * @param sessionId session id should match {@link JobManager}
     * @param id uses this value to construct database name {@code "db_" + id}
     * @param jobSerializer The serializer to use while persisting jobs to database
     * @param inTestMode If true, creates a memory only database
     */
    public SqliteJobQueue(Context context, long sessionId, String id, JobSerializer jobSerializer,
            boolean inTestMode, Timer timer) {
        this.timer = timer;
        this.sessionId = sessionId;
        dbOpenHelper = new DbOpenHelper(context, inTestMode ? null : ("db_" + id));
        db = dbOpenHelper.getWritableDatabase();
        sqlHelper = new SqlHelper(db, DbOpenHelper.JOB_HOLDER_TABLE_NAME,
                DbOpenHelper.ID_COLUMN.columnName, DbOpenHelper.COLUMN_COUNT,
                DbOpenHelper.JOB_TAGS_TABLE_NAME, DbOpenHelper.TAGS_COLUMN_COUNT, sessionId);
        this.jobSerializer = jobSerializer;
        readyJobsQueryCache = new QueryCache();
        nextJobsQueryCache = new QueryCache();
        nextJobDelayUntilQueryCache = new QueryCache();
        sqlHelper.resetDelayTimesTo(JobManager.NOT_DELAYED_JOB_DELAY);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insert(JobHolder jobHolder) {
        if (jobHolder.hasTags()) {
            return insertWithTags(jobHolder);
        }
        final SQLiteStatement stmt = sqlHelper.getInsertStatement();
        stmt.clearBindings();
        bindValues(stmt, jobHolder);
        long insertId = stmt.executeInsert();
        // insert id is a alias to row_id
        jobHolder.setInsertionOrder(insertId);
        return insertId != -1;
    }

    private boolean insertWithTags(JobHolder jobHolder) {
        final SQLiteStatement stmt = sqlHelper.getInsertStatement();
        final SQLiteStatement tagsStmt = sqlHelper.getInsertTagsStatement();
        db.beginTransaction();
        try {
            stmt.clearBindings();
            bindValues(stmt, jobHolder);
            boolean insertResult = stmt.executeInsert() != -1;
            if (!insertResult) {
                return false;
            }
            for (String tag : jobHolder.getTags()) {
                tagsStmt.clearBindings();
                bindTag(tagsStmt, jobHolder.getId(), tag);
                tagsStmt.executeInsert();
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Throwable t) {
            JqLog.e(t, "error while inserting job with tags");
            return false;
        }
        finally {
            db.endTransaction();
        }
    }

    private void bindTag(SQLiteStatement stmt, String jobId, String tag) {
        stmt.bindString(DbOpenHelper.TAGS_JOB_ID_COLUMN.columnIndex + 1, jobId);
        stmt.bindString(DbOpenHelper.TAGS_NAME_COLUMN.columnIndex + 1, tag);
    }

    private void bindValues(SQLiteStatement stmt, JobHolder jobHolder) {
        if (jobHolder.getInsertionOrder() != null) {
            stmt.bindLong(DbOpenHelper.INSERTION_ORDER_COLUMN.columnIndex + 1, jobHolder.getInsertionOrder());
        }

        stmt.bindString(DbOpenHelper.ID_COLUMN.columnIndex + 1, jobHolder.getId());
        stmt.bindLong(DbOpenHelper.PRIORITY_COLUMN.columnIndex + 1, jobHolder.getPriority());
        if(jobHolder.getGroupId() != null) {
            stmt.bindString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex + 1, jobHolder.getGroupId());
        }
        stmt.bindLong(DbOpenHelper.RUN_COUNT_COLUMN.columnIndex + 1, jobHolder.getRunCount());
        byte[] job = getSerializeJob(jobHolder);
        if (job != null) {
            stmt.bindBlob(DbOpenHelper.BASE_JOB_COLUMN.columnIndex + 1, job);
        }
        stmt.bindLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex + 1, jobHolder.getCreatedNs());
        stmt.bindLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex + 1, jobHolder.getDelayUntilNs());
        stmt.bindLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex + 1, jobHolder.getRunningSessionId());
        stmt.bindLong(DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnIndex + 1,
                jobHolder.requiresNetwork() ? 1L : 0L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insertOrReplace(JobHolder jobHolder) {
        if (jobHolder.getInsertionOrder() == null) {
            return insert(jobHolder);
        }
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        SQLiteStatement stmt = sqlHelper.getInsertOrReplaceStatement();
        stmt.clearBindings();
        bindValues(stmt, jobHolder);
        return stmt.executeInsert() != -1;
    }

    public String logJobs() {
        StringBuilder sb =  new StringBuilder();
        String where = createReadyJobWhereSql(true, null, false);
        String select = sqlHelper.createSelect(
                where,
                100,
                new SqlHelper.Order(DbOpenHelper.PRIORITY_COLUMN,
                        SqlHelper.Order.Type.DESC),
                new SqlHelper.Order(DbOpenHelper.CREATED_NS_COLUMN,
                        SqlHelper.Order.Type.ASC),
                new SqlHelper.Order(DbOpenHelper.INSERTION_ORDER_COLUMN, SqlHelper.Order.Type.ASC)
        );
        Cursor cursor = db.rawQuery(select, new String[]{"", String.valueOf(Long.MAX_VALUE)});
        try {
            while (cursor.moveToNext()) {
                sb.append(cursor.getLong(DbOpenHelper.INSERTION_ORDER_COLUMN.columnIndex))
                        .append(" ")
                        .append(cursor.getString(DbOpenHelper.ID_COLUMN.columnIndex)).append(" ")
                        .append(cursor.getLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex))
                        .append("\n");
            }
        } finally {
            cursor.close();
        }
        return sb.toString();
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

    private void delete(String id) {
        pendingCancelations.remove(id);
        SQLiteStatement stmt = sqlHelper.getDeleteStatement();
        stmt.clearBindings();
        stmt.bindString(1, id);
        stmt.execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int count() {
        SQLiteStatement stmt = sqlHelper.getCountStatement();
        stmt.clearBindings();
        stmt.bindLong(1, sessionId);
        return (int) stmt.simpleQueryForLong();
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
        Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(sessionId),
                Long.toString(timer.nanoTime())});
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
    public JobHolder findJobById(String id) {
        Cursor cursor = db.rawQuery(sqlHelper.FIND_BY_ID_QUERY, new String[]{id});
        try {
            if(!cursor.moveToFirst()) {
                return null;
            }
            return createJobHolderFromCursor(cursor);
        } catch (InvalidJobException e) {
            JqLog.e(e, "invalid job on findJobById");
            return null;
        } finally {
            cursor.close();
        }
    }

    @Override
    public Set<JobHolder> findJobsByTags(TagConstraint tagConstraint, boolean excludeCancelled,
            Collection<String> excludeUUIDs, String... tags) {
        if (tags == null || tags.length == 0) {
            return Collections.emptySet();
        }
        Set<JobHolder> jobs = new HashSet<>();
        int excludeCount = excludeUUIDs == null ? 0 : excludeUUIDs.size();
        if (excludeCancelled) {
            excludeCount += pendingCancelations.size();
        }
        final String query = sqlHelper.createFindByTagsQuery(tagConstraint,
                excludeCount, tags.length);
        JqLog.d(query);
        final String[] args;
        if (excludeCount == 0) {
            args = tags;
        } else {
            args = new String[excludeCount + tags.length];
            System.arraycopy(tags, 0, args, 0, tags.length);
            int i = tags.length;
            if (excludeUUIDs != null) {
                for (String ex : excludeUUIDs) {
                    args[i ++] = ex;
                }
            }
            if (excludeCancelled) {
                for (String ex : pendingCancelations) {
                    args[i ++] = ex;
                }
            }
        }
        Cursor cursor = db.rawQuery(query, args);
        try {
            while (cursor.moveToNext()) {
                jobs.add(createJobHolderFromCursor(cursor));
            }
        } catch (InvalidJobException e) {
            JqLog.e(e, "invalid job found by tags.");
        } finally {
            cursor.close();
        }
        return jobs;
    }

    @Override
    public void onJobCancelled(JobHolder holder) {
        pendingCancelations.add(holder.getId());
        setSessionIdOnJob(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount(boolean hasNetwork, Collection<String> excludeGroups) {
        //we can even keep these prepared but not sure the cost of them in db layer
        String selectQuery = nextJobsQueryCache.get(hasNetwork, excludeGroups);
        if (selectQuery == null) {
            String where = createReadyJobWhereSql(hasNetwork, excludeGroups, false);
            selectQuery = sqlHelper.createSelect(
                    where,
                    1,
                    new SqlHelper.Order(DbOpenHelper.PRIORITY_COLUMN,
                            SqlHelper.Order.Type.DESC),
                    new SqlHelper.Order(DbOpenHelper.CREATED_NS_COLUMN,
                            SqlHelper.Order.Type.ASC),
                    new SqlHelper.Order(DbOpenHelper.INSERTION_ORDER_COLUMN, SqlHelper.Order.Type.ASC)
            );
            nextJobsQueryCache.set(selectQuery, hasNetwork, excludeGroups);
        }
        while (true) {
            Cursor cursor = db.rawQuery(selectQuery,
                    new String[]{Long.toString(sessionId), Long.toString(timer.nanoTime())});
            try {
                if (!cursor.moveToNext()) {
                    return null;
                }
                JobHolder holder = createJobHolderFromCursor(cursor);
                setSessionIdOnJob(holder);
                return holder;
            } catch (InvalidJobException e) {
                //delete
                String jobId = cursor.getString(0);
                delete(jobId);
            } finally {
                cursor.close();
            }
        }
    }

    private String createReadyJobWhereSql(boolean hasNetwork, Collection<String> excludeGroups, boolean groupByRunningGroup) {
        String where = DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnName + " != ? "
                + " AND " + DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnName + " <= ? ";
        if(!hasNetwork) {
            where += " AND " + DbOpenHelper.REQUIRES_NETWORK_COLUMN.columnName + " != 1 ";
        }
        String groupConstraint = null;
        if(excludeGroups != null && excludeGroups.size() > 0) {
            groupConstraint = DbOpenHelper.GROUP_ID_COLUMN.columnName + " IS NULL OR " +
                    DbOpenHelper.GROUP_ID_COLUMN.columnName + " NOT IN('" +
                    SqlHelper.joinStrings("','", excludeGroups) + "')";
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextJobDelayUntilNs(boolean hasNetwork, Collection<String> excludeGroups) {
        boolean hasExcludes = excludeGroups != null && !excludeGroups.isEmpty();
        if (!hasExcludes) {
            SQLiteStatement stmt =
                    hasNetwork ? sqlHelper.getNextJobDelayedUntilWithNetworkStatement()
                            : sqlHelper.getNextJobDelayedUntilWithoutNetworkStatement();
            try {
                stmt.clearBindings();
                return stmt.simpleQueryForLong();
            } catch (SQLiteDoneException e) {
                return null;
            }
        } else {
            String sql = nextJobDelayUntilQueryCache.get(hasNetwork, excludeGroups);
            if (sql == null) {
                sql = sqlHelper.createNextJobDelayUntilQuery(hasNetwork, excludeGroups);
                nextJobDelayUntilQueryCache.set(sql, hasNetwork, excludeGroups);
            }
            Cursor cursor = db.rawQuery(sql, new String[0]);
            try {
                if(!cursor.moveToNext()) {
                    return null;
                }
                return cursor.getLong(0);
            } finally {
                cursor.close();
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

    /**
     * This method is called when a job is pulled to run.
     * It is properly marked so that it won't be returned from next job queries.
     * <p/>
     * Same mechanism is also used for cancelled jobs.
     *
     * @param jobHolder The job holder to update session id
     */
    private void setSessionIdOnJob(JobHolder jobHolder) {
        SQLiteStatement stmt = sqlHelper.getOnJobFetchedForRunningStatement();
        jobHolder.setRunCount(jobHolder.getRunCount() + 1);
        jobHolder.setRunningSessionId(sessionId);
        stmt.clearBindings();
        stmt.bindLong(1, jobHolder.getRunCount());
        stmt.bindLong(2, sessionId);
        stmt.bindString(3, jobHolder.getId());
        stmt.execute();
    }

    private JobHolder createJobHolderFromCursor(Cursor cursor) throws InvalidJobException {
        Job job = safeDeserialize(cursor.getBlob(DbOpenHelper.BASE_JOB_COLUMN.columnIndex));
        if (job == null) {
            throw new InvalidJobException();
        }
        return new JobHolder.Builder()
                .insertionOrder(cursor.getLong(DbOpenHelper.INSERTION_ORDER_COLUMN.columnIndex))
                .priority(cursor.getInt(DbOpenHelper.PRIORITY_COLUMN.columnIndex))
                .groupId(cursor.getString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex))
                .runCount(cursor.getInt(DbOpenHelper.RUN_COUNT_COLUMN.columnIndex))
                .job(job)
                .createdNs(cursor.getLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex))
                .delayUntilNs(cursor.getLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex))
                .runningSessionId(cursor.getLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex))
                .build();

    }

    private Job safeDeserialize(byte[] bytes) {
        try {
            return jobSerializer.deserialize(bytes);
        } catch (Throwable t) {
            JqLog.e(t, "error while deserializing job");
        }
        return null;
    }

    private byte[] getSerializeJob(JobHolder jobHolder) {
        return safeSerialize(jobHolder.getJob());
    }

    private byte[] safeSerialize(Object object) {
        try {
            return jobSerializer.serialize(object);
        } catch (Throwable t) {
            JqLog.e(t, "error while serializing object %s", object.getClass().getSimpleName());
        }
        return null;
    }

    private static class InvalidJobException extends Exception {

    }

    public static class JavaSerializer implements JobSerializer {

        @Override
        public byte[] serialize(Object object) throws IOException {
            if (object == null) {
                return null;
            }
            ByteArrayOutputStream bos = null;
            try {
                bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
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
        public <T extends Job> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ObjectInputStream in = null;
            try {
                in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                //noinspection unchecked
                return (T) in.readObject();
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }
    }

    public interface JobSerializer {
        byte[] serialize(Object object) throws IOException;
        <T extends Job> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException;
    }
}
