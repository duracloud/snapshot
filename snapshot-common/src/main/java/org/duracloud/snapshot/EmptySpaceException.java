/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot;

import org.duracloud.snapshot.SnapshotException;

/**
 * @author Daniel Bernstein
 *         Date: Feb 22, 2018
 */
public class EmptySpaceException extends SnapshotException {
    public EmptySpaceException(final String message) {
        super(message, null);
    }

}
