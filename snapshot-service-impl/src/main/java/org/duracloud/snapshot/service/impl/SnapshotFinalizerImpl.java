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

import org.duracloud.snapshot.service.SnapshotFinalizer;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Aug 18, 2014
 */
@Component
public class SnapshotFinalizerImpl implements SnapshotFinalizer {
    private static Logger log = LoggerFactory.getLogger(SnapshotFinalizerImpl.class);
    private Timer timer;

    @Autowired
    private SnapshotManager snapshotManager;
    
    /**
     * @param snapshotManager the snapshotManager to set
     */
    public void setSnapshotManager(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }
    

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotFinalizer#initialize(java.lang.Integer)
     */
    @Override
    public void initialize(Integer pollingPeriodMs) {
         if(timer == null){
            timer = new Timer();
            TimerTask task = new TimerTask(){
                /* (non-Javadoc)
                 * @see java.util.TimerTask#run()
                 */
                @Override
                public void run() {
                    try{
                        log.info("launching periodic snapshot finalization...");
                        snapshotManager.finalizeSnapshots();
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                }
            };

            if(pollingPeriodMs  == null ||  pollingPeriodMs <  1){
                pollingPeriodMs = DEFAULT_POLLING_PERIOD_MS;
            }

            timer.schedule(task, new Date(), pollingPeriodMs);
            log.info("snapshot finalization scheduled to run every "
                + pollingPeriodMs + " milliseconds.");

         }
        
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.CleanupManager#destroy()
     */
    @Override
    public void destroy() {
        if(timer != null){
            timer.cancel();
            timer = null;
        }
    }
}
