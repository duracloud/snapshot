/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.io.File;

import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Jul 25, 2014
 */
@Component
public class BridgeConfiguration {
    private String[] duracloudEmailAddresses;
    private String duracloudUsername;
    private String duracloudPassword;
    private File contentRootDir;
    /**
     * @return the duracloudUsername
     */
    public String getDuracloudUsername() {
        return duracloudUsername;
    }

    /**
     * @param duracloudUsername the duracloudUsername to set
     */
    public void setDuracloudUsername(String duracloudUsername) {
        this.duracloudUsername = duracloudUsername;
    }

    /**
     * @return the duracloudPassword
     */
    public String getDuracloudPassword() {
        return duracloudPassword;
    }

    /**
     * @param duracloudPassword the duracloudPassword to set
     */
    public void setDuracloudPassword(String duracloudPassword) {
        this.duracloudPassword = duracloudPassword;
    }

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

    /**
     * @return the contentRootDir
     */
    public File getContentRootDir() {
        return contentRootDir;
    }

    /**
     * @param contentRootDir the contentRootDir to set
     */
    public void setContentRootDir(File contentRootDir) {
        this.contentRootDir = contentRootDir;
    }
    
    
}
