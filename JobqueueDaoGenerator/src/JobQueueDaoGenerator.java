import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Schema;

import java.io.IOException;

public class JobQueueDaoGenerator extends DaoGenerator {
    public JobQueueDaoGenerator() throws IOException {
    }

    public static void main(String[] args) {
        Schema schema = new Schema(1, "com.path.android.jobqueue");
        schema.setDefaultJavaPackageTest("com.path.android.jobqueue.test");
        schema.setDefaultJavaPackageDao("com.path.android.jobqueue.dao");
        schema.enableKeepSectionsByDefault();

        Entity job = schema.addEntity("JobHolder");
        job.addIdProperty();
        job.addIntProperty("priority").indexAsc(null, false);
        job.addIntProperty("runCount");
        job.addSerializedProperty("baseJob", "com.path.android.jobqueue.BaseJob");
        job.addDateProperty("created");
        job.addLongProperty("runningSessionId");
        try {
            new DaoGenerator().generateAll(schema, "jobqueue/src-gen", "jobqueue/src");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
