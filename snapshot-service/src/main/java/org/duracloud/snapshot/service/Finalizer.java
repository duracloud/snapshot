/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

/**
 * Waits for conditions which signal that snapshot and restore actions should be
 * moved to their final state, and kicks off the work to make that happen.
 *
 * @author Daniel Bernstein
 *         Date: Aug 18, 2014
 */
public interface Finalizer {
    public static final Integer DEFAULT_POLLING_PERIOD_MS = 60*60*1000;

    /**
     * The time in milliseconds between checks for clean snapshots.
     * @param pollingPeriodMs Null value or value less than 1 will be set to the default of 1 hour.
     */
    public void initialize(Integer pollingPeriodMs);
    public void destroy();
}
