/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

/**
 * @author Daniel Bernstein
 *         Date: Jul 23, 2014
 */
public class SnapshotAlreadyExistsException extends Exception {
    public SnapshotAlreadyExistsException(String message) {
        super(message);
    }
}
