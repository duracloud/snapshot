/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager;

/**
 * @author Daniel Bernstein
 *         Date: Jul 14, 2014
 */
public class SnapshotInProcessException extends SnapshotException {
    public SnapshotInProcessException(String message){
        super(message, null);
    }
}
