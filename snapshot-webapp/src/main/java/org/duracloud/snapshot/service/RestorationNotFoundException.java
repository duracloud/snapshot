/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.manager.SnapshotException;

/**
 * @author Daniel Bernstein
 *         Date: Jul 23, 2014
 */
public class RestorationNotFoundException extends SnapshotException {
    public RestorationNotFoundException(Long restorationId){
        super("No restoration with id = " + restorationId + " found.",null);
    }
}
