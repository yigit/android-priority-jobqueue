package com.birbit.android.jobqueue.persistentQueue.sqlite;


import android.content.Context;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.log.JqLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides a file based storage to keep jobs
 */
public class FileStorage {
    private static final String EXT = ".jobs";
    private File folder;
    public FileStorage(Context appContext, String id) {
        this.folder = new File(appContext.getDir("com_birbit_jobqueue_jobs", Context.MODE_PRIVATE),
                "files_" + id);
        //noinspection ResultOfMethodCallIgnored
        this.folder.mkdirs();
    }

    public void delete(String id) {
        File file = new File(folder, filename(id));
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Nullable
    public byte[] load(String id) throws IOException {
        File file = new File(folder, filename(id));
        if (file.exists() && file.canRead()) {
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("File is too big");
            }
            byte[] result = new byte[(int) length];
            FileInputStream fis = new FileInputStream(file);
            try {
                fis.read(result);
            } finally {
                fis.close();
            }
            return result;
        }
        return null;
    }

    public void save(String id, byte[] data) throws IOException {
        File file = new File(folder, filename(id));
        if (file.exists()) {
            file.delete();
        }
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
    }

    private static String filename(String id) {
        return id + EXT;
    }

    @Nullable
    private static String filenameToId(String filename) {
        if (filename.length() < EXT.length() + 1) {
            return null;
        }
        return filename.substring(0, filename.length() - EXT.length());
    }

    public void truncateExcept(Set<String> ids) {
        for (String filename : folder.list()) {
            if (!filename.endsWith(EXT)) {
                continue;
            }
            String id = filenameToId(filename);
            if (!ids.contains(id)) {
                File file = new File(folder, filename);
                if (!file.delete()) {
                    JqLog.d("cannot delete unused job file " + file.getAbsolutePath());
                }
            }
        }
    }
}
