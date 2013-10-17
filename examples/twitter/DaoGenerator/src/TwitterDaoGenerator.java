import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Schema;

import java.io.IOException;

public class TwitterDaoGenerator extends DaoGenerator {
    public TwitterDaoGenerator() throws IOException {
    }
    public static void main(String[] args) {
        Schema schema = new Schema(3, "com.path.android.jobqueue.examples.twitter.entities");
        schema.setDefaultJavaPackageTest("com.path.android.jobqueue.examples.twitter.test");
        schema.setDefaultJavaPackageDao("com.path.android.jobqueue.examples.twitter.dao");
        schema.enableKeepSectionsByDefault();
        Entity tweet = schema.addEntity("Tweet");
        tweet.addLongProperty("localId").primaryKey().autoincrement();
        tweet.addLongProperty("serverId").unique();
        tweet.addStringProperty("text");
        tweet.addLongProperty("userId");
        tweet.addBooleanProperty("isLocal");
        tweet.addDateProperty("createdAt");
        try {
            new DaoGenerator().generateAll(schema, "src-gen", "src");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
