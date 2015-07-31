/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.dto.RestoreStatus;
import static org.duracloud.snapshot.dto.RestoreStatus.*;

/**
 * @author Daniel Bernstein
 *         Date: Jul 30, 2014
 */
public class RestorationStateTransitionValidator {
    public static void validate(RestoreStatus from , RestoreStatus to)
        throws InvalidStateTransitionException{

        if(from.equals(to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }
}
