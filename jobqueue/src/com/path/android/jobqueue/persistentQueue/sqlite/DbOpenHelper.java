package com.path.android.jobqueue.persistentQueue.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Helper class for {@link SqliteJobQueue} to handle database connection
 */
public class DbOpenHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 3;
    /*package*/ static final String JOB_HOLDER_TABLE_NAME = "job_holder";
    /*package*/ static final SqlHelper.Property ID_COLUMN = new SqlHelper.Property("_id", "integer", 0);
    /*package*/ static final SqlHelper.Property PRIORITY_COLUMN = new SqlHelper.Property("priority", "integer", 1);
    /*package*/ static final SqlHelper.Property GROUP_ID_COLUMN = new SqlHelper.Property("group_id", "text", 2);
    /*package*/ static final SqlHelper.Property RUN_COUNT_COLUMN = new SqlHelper.Property("run_count", "integer", 3);
    /*package*/ static final SqlHelper.Property BASE_JOB_COLUMN = new SqlHelper.Property("base_job", "byte", 4);
    /*package*/ static final SqlHelper.Property CREATED_NS_COLUMN = new SqlHelper.Property("created_ns", "long", 5);
    /*package*/ static final SqlHelper.Property DELAY_UNTIL_NS_COLUMN = new SqlHelper.Property("delay_until_ns", "long", 6);
    /*package*/ static final SqlHelper.Property RUNNING_SESSION_ID_COLUMN = new SqlHelper.Property("running_session_id", "long", 7);
    /*package*/ static final SqlHelper.Property REQUIRES_NETWORK_COLUMN = new SqlHelper.Property("requires_network", "integer", 8);

    /*package*/ static final int COLUMN_COUNT = 9;

    public DbOpenHelper(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String createQuery = SqlHelper.create(JOB_HOLDER_TABLE_NAME,
                ID_COLUMN,
                PRIORITY_COLUMN,
                GROUP_ID_COLUMN,
                RUN_COUNT_COLUMN,
                BASE_JOB_COLUMN,
                CREATED_NS_COLUMN,
                DELAY_UNTIL_NS_COLUMN,
                RUNNING_SESSION_ID_COLUMN,
                REQUIRES_NETWORK_COLUMN
        );
        sqLiteDatabase.execSQL(createQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL(SqlHelper.drop(JOB_HOLDER_TABLE_NAME));
        onCreate(sqLiteDatabase);
    }
}
