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
 *         Date: Jul 30, 2014
 */
public class InvalidStateTransitionException extends SnapshotException {

    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(String oldState, String newState){
        super("Moving from " + oldState + " to " + newState + " is not allowed.", null);
    }
}
