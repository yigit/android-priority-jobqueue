package com.birbit.android.jobqueue.persistentQueue.sqlite;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.log.JqLog;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent Job Queue that keeps its data in an sqlite database.
 */
public class SqliteJobQueue implements JobQueue {
    @SuppressWarnings("FieldCanBeLocal")
    private DbOpenHelper dbOpenHelper;
    private final long sessionId;
    private SQLiteDatabase db;
    private SqlHelper sqlHelper;
    private JobSerializer jobSerializer;
    private FileStorage jobStorage;
    private final StringBuilder reusedStringBuilder = new StringBuilder();
    private final WhereQueryCache whereQueryCache;

    public SqliteJobQueue(Configuration configuration, long sessionId, JobSerializer serializer) {
        this.sessionId = sessionId;
        jobStorage = new FileStorage(configuration.getAppContext(), "jobs_" + configuration.getId());
        whereQueryCache = new WhereQueryCache(sessionId);
        dbOpenHelper = new DbOpenHelper(configuration.getAppContext(),
                configuration.isInTestMode() ? null : ("db_" + configuration.getId()));
        db = dbOpenHelper.getWritableDatabase();
        sqlHelper = new SqlHelper(db, DbOpenHelper.JOB_HOLDER_TABLE_NAME,
                DbOpenHelper.ID_COLUMN.columnName, DbOpenHelper.COLUMN_COUNT,
                DbOpenHelper.JOB_TAGS_TABLE_NAME, DbOpenHelper.TAGS_COLUMN_COUNT, sessionId);
        this.jobSerializer = serializer;
        if (configuration.resetDelaysOnRestart()) {
            sqlHelper.resetDelayTimesTo(JobManager.NOT_DELAYED_JOB_DELAY);
        }
        reEnablePendingCancellations();
        cleanupFiles();
    }

    private void reEnablePendingCancellations() {
        // if we had jobs that were cancelled but the cancellation could not complete, re-enable
        // them. Looks like the app crashed before cancel could be completed.
        db.execSQL(sqlHelper.RE_ENABLE_PENDING_CANCELLATIONS_QUERY);
    }

    private void cleanupFiles() {
        Cursor cursor = db.rawQuery(sqlHelper.LOAD_ALL_IDS_QUERY, null);
        Set<String> jobIds = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                jobIds.add(cursor.getString(0));
            }
        } finally {
            cursor.close();
        }
        jobStorage.truncateExcept(jobIds);
    }

    @VisibleForTesting
    public SQLiteDatabase getDb() {
        return db;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insert(@NonNull JobHolder jobHolder) {
        persistJobToDisk(jobHolder);
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

    private void persistJobToDisk(@NonNull JobHolder jobHolder) {
        try {
            jobStorage.save(jobHolder.getId(), jobSerializer.serialize(jobHolder.getJob()));
        } catch (IOException e) {
            throw new RuntimeException("cannot save job to disk", e);
        }
    }

    @Override
    public void substitute(@NonNull JobHolder newJob, @NonNull JobHolder oldJob) {
        db.beginTransaction();
        try {
            remove(oldJob);
            insert(newJob);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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
        stmt.bindLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex + 1, jobHolder.getCreatedNs());
        stmt.bindLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex + 1, jobHolder.getDelayUntilNs());
        stmt.bindLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex + 1, jobHolder.getRunningSessionId());
        stmt.bindLong(DbOpenHelper.REQUIRED_NETWORK_TYPE_COLUMN.columnIndex + 1,
                jobHolder.getRequiredNetworkType());
        stmt.bindLong(DbOpenHelper.DEADLINE_COLUMN.columnIndex + 1,
                jobHolder.getDeadlineNs());
        stmt.bindLong(DbOpenHelper.CANCEL_ON_DEADLINE_COLUMN.columnIndex + 1,
                jobHolder.shouldCancelOnDeadline() ? 1 : 0);
        stmt.bindLong(DbOpenHelper.CANCELLED_COLUMN.columnIndex + 1, jobHolder.isCancelled() ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insertOrReplace(@NonNull JobHolder jobHolder) {
        if (jobHolder.getInsertionOrder() == null) {
            return insert(jobHolder);
        }
        persistJobToDisk(jobHolder);
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        SQLiteStatement stmt = sqlHelper.getInsertOrReplaceStatement();
        stmt.clearBindings();
        bindValues(stmt, jobHolder);
        boolean result = stmt.executeInsert() != -1;
        JqLog.d("reinsert job result %s", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(@NonNull JobHolder jobHolder) {
        delete(jobHolder.getId());
    }

    private void delete(String id) {
        db.beginTransaction();
        try {
            SQLiteStatement stmt = sqlHelper.getDeleteStatement();
            stmt.clearBindings();
            stmt.bindString(1, id);
            stmt.execute();
            SQLiteStatement deleteTagsStmt = sqlHelper.getDeleteJobTagsStatement();
            deleteTagsStmt.bindString(1, id);
            deleteTagsStmt.execute();
            db.setTransactionSuccessful();
            jobStorage.delete(id);
        } finally {
            db.endTransaction();
        }
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
    public int countReadyJobs(@NonNull Constraint constraint) {
        final Where where = createWhere(constraint);
        final long result = where.countReady(db, reusedStringBuilder).simpleQueryForLong();
        return (int) result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder findJobById(@NonNull String id) {
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

    @NonNull
    @Override
    public Set<JobHolder> findJobs(@NonNull Constraint constraint) {
        final Where where = createWhere(constraint);
        String selectQuery = where.findJobs(sqlHelper);
        Cursor cursor = db.rawQuery(selectQuery, where.args);
        Set<JobHolder> jobs = new HashSet<>();
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
    public void onJobCancelled(JobHolder jobHolder) {
        SQLiteStatement stmt = sqlHelper.getMarkAsCancelledStatement();
        stmt.clearBindings();
        stmt.bindString(1, jobHolder.getId());
        stmt.execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount(@NonNull Constraint constraint) {
        final Where where = createWhere(constraint);
        //we can even keep these prepared but not sure the cost of them in db layer
        final String selectQuery = where.nextJob(sqlHelper);
        while (true) {
            Cursor cursor = db.rawQuery(selectQuery, where.args);
            try {
                if (!cursor.moveToNext()) {
                    return null;
                }
                JobHolder holder = createJobHolderFromCursor(cursor);
                setSessionIdOnJob(holder);
                return holder;
            } catch (InvalidJobException e) {
                //delete
                String jobId = cursor.getString(DbOpenHelper.ID_COLUMN.columnIndex);
                if (jobId == null) {
                    JqLog.e("cannot find job id on a retrieved job");
                } else {
                    delete(jobId);
                }
            } finally {
                cursor.close();
            }
        }
    }

    private Where createWhere(Constraint constraint) {
        return whereQueryCache.build(constraint, reusedStringBuilder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextJobDelayUntilNs(@NonNull Constraint constraint) {
        final Where where = createWhere(constraint);
        try {
            long result = where.nextJobDelayUntil(db, sqlHelper).simpleQueryForLong();
            return result == Params.FOREVER ? null : result;
        } catch (SQLiteDoneException empty) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        sqlHelper.truncate();
        cleanupFiles();
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

    @SuppressWarnings("unused")
    public String logJobs() {
        StringBuilder sb =  new StringBuilder();
        String select = sqlHelper.createSelect(
                null,
                100,
                new SqlHelper.Order(DbOpenHelper.PRIORITY_COLUMN,
                        SqlHelper.Order.Type.DESC),
                new SqlHelper.Order(DbOpenHelper.CREATED_NS_COLUMN,
                        SqlHelper.Order.Type.ASC),
                new SqlHelper.Order(DbOpenHelper.INSERTION_ORDER_COLUMN, SqlHelper.Order.Type.ASC)
        );
        Cursor cursor = db.rawQuery(select, new String[0]);
        try {
            while (cursor.moveToNext()) {
                String id = cursor.getString(DbOpenHelper.ID_COLUMN.columnIndex);
                sb.append(cursor.getLong(DbOpenHelper.INSERTION_ORDER_COLUMN.columnIndex))
                        .append(" ")
                        .append(id).append(" id:")
                        .append(cursor.getString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex))
                        .append(" deadline:")
                        .append(cursor.getLong(DbOpenHelper.DEADLINE_COLUMN.columnIndex))
                        .append(" cancelled:")
                        .append(cursor.getInt(DbOpenHelper.CANCELLED_COLUMN.columnIndex) == 1)
                        .append(" delay until:")
                        .append(cursor.getLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex))
                        .append(" sessionId:")
                        .append(cursor.getLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex))
                        .append(" reqNetworkType:")
                        .append(cursor.getLong(DbOpenHelper.REQUIRED_NETWORK_TYPE_COLUMN.columnIndex));
                Cursor tags = db.rawQuery("SELECT " + DbOpenHelper.TAGS_NAME_COLUMN.columnName
                        + " FROM " + DbOpenHelper.JOB_TAGS_TABLE_NAME + " WHERE "
                        + DbOpenHelper.TAGS_JOB_ID_COLUMN.columnName + " = ?", new String[]{id});
                try {
                    while (tags.moveToNext()) {
                        sb.append(", ").append(tags.getString(0));
                    }
                } finally {
                    tags.close();
                }
                sb.append("\n");

            }
        } finally {
            cursor.close();
        }
        return sb.toString();
    }

    private JobHolder createJobHolderFromCursor(Cursor cursor) throws InvalidJobException {
        String jobId = cursor.getString(DbOpenHelper.ID_COLUMN.columnIndex);
        Job job;
        try {
            job = safeDeserialize(jobStorage.load(jobId));
        } catch (IOException e) {
            throw new InvalidJobException("cannot load job from disk", e);
        }
        if (job == null) {
            throw new InvalidJobException("null job");
        }
        // load tags
        Set<String> tags = loadTags(jobId);
        //noinspection WrongConstant,UnnecessaryLocalVariable
        JobHolder holder = new JobHolder.Builder()
                .insertionOrder(cursor.getLong(DbOpenHelper.INSERTION_ORDER_COLUMN.columnIndex))
                .priority(cursor.getInt(DbOpenHelper.PRIORITY_COLUMN.columnIndex))
                .groupId(cursor.getString(DbOpenHelper.GROUP_ID_COLUMN.columnIndex))
                .runCount(cursor.getInt(DbOpenHelper.RUN_COUNT_COLUMN.columnIndex))
                .job(job)
                .id(jobId)
                .tags(tags)
                .persistent(true)
                .deadline(cursor.getLong(DbOpenHelper.DEADLINE_COLUMN.columnIndex),
                        cursor.getInt(DbOpenHelper.CANCEL_ON_DEADLINE_COLUMN.columnIndex) == 1)
                .createdNs(cursor.getLong(DbOpenHelper.CREATED_NS_COLUMN.columnIndex))
                .delayUntilNs(cursor.getLong(DbOpenHelper.DELAY_UNTIL_NS_COLUMN.columnIndex))
                .runningSessionId(cursor.getLong(DbOpenHelper.RUNNING_SESSION_ID_COLUMN.columnIndex))
                .requiredNetworkType(cursor.getInt(DbOpenHelper.REQUIRED_NETWORK_TYPE_COLUMN.columnIndex))
                .build();
        return holder;
    }

    private Set<String> loadTags(String jobId) {
        Cursor cursor = db.rawQuery(sqlHelper.LOAD_TAGS_QUERY, new String[]{jobId});
        try {
            if (cursor.getCount() == 0) {
                //noinspection unchecked
                return Collections.EMPTY_SET;
            }
            final Set<String> tags = new HashSet<>();
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(0));
            }
            return tags;
        } finally {
            cursor.close();
        }
    }

    private Job safeDeserialize(byte[] bytes) {
        try {
            return jobSerializer.deserialize(bytes);
        } catch (Throwable t) {
            JqLog.e(t, "error while deserializing job");
        }
        return null;
    }

    @SuppressWarnings("WeakerAccess")
    static class InvalidJobException extends Exception {
        InvalidJobException(String detailMessage) {
            super(detailMessage);
        }

        InvalidJobException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
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
