package com.path.android.jobqueue.persistentQueue.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbOpenHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;
    /*package*/ static final String JOB_HOLDER_TABLE_NAME = "job_holder";
    /*package*/ static final SqlHelper.Property ID_COLUMN = new SqlHelper.Property("_id", "integer");
    /*package*/ static final SqlHelper.Property PRIORITY_COLUMN = new SqlHelper.Property("priority", "integer");
    /*package*/ static final SqlHelper.Property RUN_COUNT_COLUMN = new SqlHelper.Property("run_count", "integer");
    /*package*/ static final SqlHelper.Property BASE_JOB_COLUMN = new SqlHelper.Property("base_job", "byte");
    /*package*/ static final SqlHelper.Property CREATED_COLUMN = new SqlHelper.Property("created", "long");
    /*package*/ static final SqlHelper.Property RUNNING_SESSION_ID_COLUMN = new SqlHelper.Property("running_session_id", "long");
    /*package*/ static final int COLUMN_COUNT = 6;

    public DbOpenHelper(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String createQuery = SqlHelper.create(JOB_HOLDER_TABLE_NAME,
                ID_COLUMN,
                PRIORITY_COLUMN,
                RUN_COUNT_COLUMN,
                BASE_JOB_COLUMN,
                CREATED_COLUMN,
                RUNNING_SESSION_ID_COLUMN
                );
        sqLiteDatabase.execSQL(createQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL(SqlHelper.drop(JOB_HOLDER_TABLE_NAME));
        onCreate(sqLiteDatabase);
    }
}
