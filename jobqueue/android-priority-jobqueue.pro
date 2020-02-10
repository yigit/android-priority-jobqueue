# Gcm support is a compileOnly dependency, no need to warn
-dontwarn com.birbit.android.jobqueue.scheduling.GcmJobSchedulerService
-dontwarn com.birbit.android.jobqueue.scheduling.GcmScheduler
-dontwarn com.google.android.gms.gcm.GcmTaskService

# Keep Job and its serialVersionUID information
-keepnames class com.birbit.android.jobqueue.Job
-keepclassmembers class com.birbit.android.jobqueue.Job {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}