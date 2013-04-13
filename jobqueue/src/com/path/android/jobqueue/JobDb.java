package com.path.android.jobqueue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.path.android.jobqueue.dao.DaoMaster;
import com.path.android.jobqueue.dao.DaoSession;
import com.path.android.jobqueue.dao.JobHolderDao;
import com.path.android.jobqueue.log.JqLog;
import de.greenrobot.dao.*;

public class JobDb {
    private static final String DEFAULT_DB_NAME = "jobs.db";

    private DaoMaster daoMaster;
    private DaoSession daoSession;
    private Query<JobHolder> _nextJobQuery;
    private CountQuery<JobHolder> _countQuery;

    private long sessionId;
    private String id;

    public JobDb(Context context, long sessionId, String id) {
        this.id = id;
        this.sessionId = sessionId;
        DaoMaster.DevOpenHelper devOpenHelper = new DaoMaster.DevOpenHelper(context.getApplicationContext(),getDbName(), null);
        SQLiteDatabase db = devOpenHelper.getWritableDatabase();
        daoMaster = new DaoMaster(db);
        daoSession = daoMaster.newSession();
        if(JqLog.isDebugEnabled()) {
            QueryBuilder.LOG_SQL = true;
            QueryBuilder.LOG_VALUES = true;
        }
    }

    protected String getDbName() {
        return id + "_" + DEFAULT_DB_NAME;
    }

    public long insert(JobHolder jobHolder) {
        return daoSession.getJobHolderDao().insert(jobHolder);
    }

    public long insertOrReplace(JobHolder jobHolder) {
        return daoSession.getJobHolderDao().insertOrReplace(jobHolder);
    }

    public void remove(JobHolder jobHolder) {
        daoSession.getJobHolderDao().delete(jobHolder);
    }

    public long count() {
        return getCountQuery().count();
    }

    public JobHolder nextJob() {
        JobHolder holder = null;
        do {
            holder = getNextJobQuery().unique();
            if(holder == null) {
                return null;
            }
            holder.setRunningSessionId(sessionId);
            //we do it here not to serialize base job again.
            daoSession.insertOrReplace(holder);
            if(holder.getBaseJob() == null) {
                JqLog.e("bad job %s", holder.getId());
                daoSession.delete(holder);
            } else {
                return holder;
            }
        } while (true);
    }

    protected Query<JobHolder> getNextJobQuery() {
        if(_nextJobQuery == null) {
            _nextJobQuery = daoSession.getJobHolderDao().queryBuilder()
                    .where(JobHolderDao.Properties.RunningSessionId.notEq(sessionId))
                    .limit(1)
                    .orderDesc(JobHolderDao.Properties.Priority)
                    .orderAsc(JobHolderDao.Properties.Id)
                    .build();
        }
        return _nextJobQuery;
    }

    protected CountQuery<JobHolder> getCountQuery() {
        if(_countQuery == null) {
            _countQuery = daoSession.getJobHolderDao().queryBuilder()
                    .where(JobHolderDao.Properties.RunningSessionId.notEq(sessionId))
                    .buildCount();
        }
        return _countQuery;
    }
}
