/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.SnapshotException;

/**
 * @author Daniel Bernstein
 * Date: Sep 24, 2015
 */
public class AlreadyInitializedException extends SnapshotException {
    public AlreadyInitializedException(String message) {
        super(message, null);
    }
}
