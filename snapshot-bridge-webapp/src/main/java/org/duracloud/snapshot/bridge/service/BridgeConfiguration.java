/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.service;

import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Jul 25, 2014
 */
@Component
public class BridgeConfiguration {
    private String[] duracloudEmailAddresses;

    /**
     * @return the duracloudAdminEmails
     */
    public String[] getDuracloudEmailAddresses() {
        return duracloudEmailAddresses;
    }

    /**
     * @param duracloudAdminEmails the duracloudAdminEmails to set
     */
    public void setDuracloudEmailAddresses(String[] duracloudAdminEmails) {
        this.duracloudEmailAddresses = duracloudAdminEmails;
    }
    
    
}
