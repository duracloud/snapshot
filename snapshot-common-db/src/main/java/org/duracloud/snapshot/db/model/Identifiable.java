/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.model;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
public interface Identifiable {
    public Long getId();
    public void setId(Long id);
}
