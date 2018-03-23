/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import javax.persistence.EntityManager;
import javax.persistence.ParameterMode;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein
 * Date: Jan 15, 2016
 */
@Component
public class PurgeObsoleteDataTask implements Runnable {

    private Logger log = LoggerFactory.getLogger(PurgeObsoleteDataTask.class);
    @PersistenceContext
    private EntityManager entityManager;

    /* (non-Javadoc)
     * @see java.util.TimerTask#run()
     */
    @Override
    @Transactional
    public void run() {
        String storedProc = "purge_obsolete_batch_data";
        boolean result = entityManager.createStoredProcedureQuery(storedProc)
                                      .registerStoredProcedureParameter(0, Integer.class, ParameterMode.IN)
                                      .setParameter(0, 90)
                                      .execute();
        log.info("executed {} : resultset ? {}", storedProc, result);
    }
}
