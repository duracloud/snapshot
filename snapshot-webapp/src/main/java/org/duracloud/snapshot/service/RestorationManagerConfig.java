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
 *         Date: Jul 15, 2014
 */
public class RestorationManagerConfig {
    private String restorationRootDir;
    private String duracloudUsername;
    private String duracloudPassword;
    private String[] duracloudEmailAddresses;
    private String[] dpnEmailAddresses;

    public RestorationManagerConfig(){}
    
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
     * @return the dpnEmailAddresses
     */
    public String[] getDpnEmailAddresses() {
        return dpnEmailAddresses;
    }

    /**
     * @param dpnEmailAddresses the dpnEmailAddresses to set
     */
    public void setDpnEmailAddresses(String[] dpnEmailAddresses) {
        this.dpnEmailAddresses = dpnEmailAddresses;
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
