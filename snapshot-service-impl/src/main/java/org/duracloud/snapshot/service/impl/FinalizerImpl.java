/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.PreDestroy;

import org.duracloud.snapshot.service.Finalizer;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author Daniel Bernstein
 * Date: Aug 18, 2014
 */
@Component
public class FinalizerImpl implements Finalizer {
    private static Logger log = LoggerFactory.getLogger(FinalizerImpl.class);
    private Timer timer;

    @Autowired
    private SnapshotManager snapshotManager;

    @Autowired
    private RestoreManager restoreManager;

    /**
     * @param snapshotManager the snapshotManager to set
     */
    public void setSnapshotManager(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    /**
     * @param restoreManager the restoreManager to set
     */
    public void setRestoreManager(RestoreManager restoreManager) {
        this.restoreManager = restoreManager;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.Finalizer#initialize(java.lang.Integer)
     */
    @Override
    public void initialize(Integer pollingPeriodMs) {
        if (timer == null) {
            timer = new Timer();
            TimerTask task = new TimerTask() {
                /* (non-Javadoc)
                 * @see java.util.TimerTask#run()
                 */
                @Override
                public void run() {
                    try {
                        log.info("Launching periodic finalization...");
                        snapshotManager.finalizeSnapshots();
                        restoreManager.finalizeRestores();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            };

            if (pollingPeriodMs == null || pollingPeriodMs < 1) {
                pollingPeriodMs = DEFAULT_POLLING_PERIOD_MS;
            }

            timer.schedule(task, new Date(), pollingPeriodMs);
            log.info("Finalization scheduled to run every "
                     + pollingPeriodMs + " milliseconds.");
        }
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.CleanupManager#destroy()
     */
    @Override
    @PreDestroy
    public void destroy() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
