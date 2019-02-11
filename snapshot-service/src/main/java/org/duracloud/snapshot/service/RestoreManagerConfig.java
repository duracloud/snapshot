/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

/**
 * @author Daniel Bernstein
 * Date: Jul 15, 2014
 */
public class RestoreManagerConfig {
    private String restorationRootDir;
    private String duracloudUsername;
    private String duracloudPassword;
    private String[] duracloudEmailAddresses;
    private String[] targetStoreEmailAddresses;

    public RestoreManagerConfig() {
    }

    /**
     * @return the restorationRootDir
     */
    public String getRestorationRootDir() {
        return restorationRootDir;
    }

    /**
     * @param restorationRootDir the restorationRootDir to set
     */
    public void setRestorationRootDir(String restorationRootDir) {
        this.restorationRootDir = restorationRootDir;
    }

    /**
     * @return the duracloudEmailAddresses
     */
    public String[] getDuracloudEmailAddresses() {
        return duracloudEmailAddresses;
    }

    /**
     * @param duracloudEmailAddresses the duracloudEmailAddresses to set
     */
    public void setDuracloudEmailAddresses(String[] duracloudEmailAddresses) {
        this.duracloudEmailAddresses = duracloudEmailAddresses;
    }

    /**
     * @return the targetStoreEmailAddresses
     */
    public String[] getTargetStoreEmailAddresses() {
        return targetStoreEmailAddresses;
    }

    /**
     * @param targetStoreEmailAddresses the targetStoreEmailAddresses to set
     */
    public void setTargetStoreEmailAddresses(String[] targetStoreEmailAddresses) {
        this.targetStoreEmailAddresses = targetStoreEmailAddresses;
    }

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

}
