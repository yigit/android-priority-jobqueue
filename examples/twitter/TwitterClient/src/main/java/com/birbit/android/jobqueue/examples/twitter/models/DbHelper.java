package com.birbit.android.jobqueue.examples.twitter.models;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.birbit.android.jobqueue.examples.twitter.TwitterApplication;
import com.birbit.android.jobqueue.examples.twitter.dao.DaoMaster;
import com.birbit.android.jobqueue.examples.twitter.dao.DaoSession;
import org.greenrobot.greendao.database.StandardDatabase;

public class DbHelper {
    private static DbHelper instance;
    private DaoSession daoSession;
    private DaoMaster daoMaster;
    private SQLiteDatabase db;

    public synchronized static DbHelper getInstance() {
        if(instance == null) {
            instance = new DbHelper();
        }
        return instance;
    }

    public DbHelper() {
        Context appContext = TwitterApplication.getInstance().getApplicationContext();
        DaoMaster.DevOpenHelper devOpenHelper = new DaoMaster.DevOpenHelper(appContext, "twitter", null);
        db = devOpenHelper.getWritableDatabase();
        StandardDatabase standardDatabase = new StandardDatabase(db);
        daoMaster = new DaoMaster(standardDatabase, DaoMaster.SCHEMA_VERSION);
        daoSession = daoMaster.newSession();
    }

    public DaoSession getDaoSession() {
        return daoSession;
    }

    public DaoMaster getDaoMaster() {
        return daoMaster;
    }
}
