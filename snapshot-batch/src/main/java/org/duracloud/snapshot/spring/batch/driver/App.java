/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.driver;

import org.duracloud.snapshot.spring.batch.DatabaseInitializer;
import org.duracloud.snapshot.spring.batch.SnapshotExecutionListener;
import org.duracloud.snapshot.spring.batch.SnapshotJobManager;
import org.duracloud.snapshot.spring.batch.SnapshotStatus;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotNotifyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Erik Paulsson
 *         Date: 2/3/14
 */
public class App {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        ConfigParser configParser =
            new ConfigParser();
        SnapshotConfig config =
            configParser.processSnapshotConfigCommandLine(args);
        DatabaseConfig dbConfig =
            configParser.processDBCommandLine(args);
        SnapshotNotifyConfig notifyConfig =
            configParser.processNotifyCommandLine(args);
        SnapshotJobManagerConfig duracloudConfig =
            configParser.processDuracloudCommandLine(args);

        String[] springConfig = {
            "spring/batch/config/context.xml",
            "spring/batch/config/database.xml"
        };
        
        ApplicationContext context =
            new ClassPathXmlApplicationContext(springConfig);

        // initialize database
        DatabaseInitializer databaseInitializer =
            (DatabaseInitializer) context.getBean("databaseInitializer");
        databaseInitializer.init(dbConfig);

        // initialize the snapshot execution listener
        SnapshotExecutionListener executionListener =
            (SnapshotExecutionListener) context.getBean("jobListener");
        executionListener.initialize(notifyConfig);

        // initialize the snapshot job manager
        SnapshotJobManager manager =
            (SnapshotJobManager) context.getBean("snapshotJobManager");
        manager.init(duracloudConfig);

        try {

            SnapshotStatus status = manager.executeSnapshot(config);
            LOGGER.info("Exit Status : {}", status);

        } catch (Exception e) {
            LOGGER.error("Error running job: " + config.getSnapshotId(), e);
        }
    }
}
