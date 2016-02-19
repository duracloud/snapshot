/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.apache.commons.lang3.StringUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bill Branan
 *         Date: 2/18/2016
 */
public class EventLog {

    private Logger log = LoggerFactory.getLogger("event-log");

    public void logSnapshotUpdate(Snapshot snapshot) {
        DuracloudEndPointConfig source = snapshot.getSource();
        log.info(format(new Object[] {"SNAPSHOT_UPDATE",
                                      snapshot.getName(),
                                      source.getHost(),
                                      snapshot.getStatus(),
                                      snapshot.getStatusText(),
                                      snapshot.getMemberId()}));
    }

    public void logRestoreUpdate(Restoration restore) {
        Snapshot snapshot = restore.getSnapshot();
        DuracloudEndPointConfig source =  snapshot.getSource();
        log.info(format(new Object[] {"RESTORE_UPDATE",
                                      restore.getRestorationId(),
                                      snapshot.getName(),
                                      source.getHost(),
                                      restore.getStatus(),
                                      restore.getStatusText(),
                                      snapshot.getMemberId()}));
    }

    private String format(Object[] objects) {
        return StringUtils.join(objects, "\t");
    }

}
