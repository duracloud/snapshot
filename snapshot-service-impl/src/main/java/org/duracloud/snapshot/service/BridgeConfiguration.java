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
    public static final String DURACLOUD_VAULT_WORKDIR_SYSTEM_PROPERTY = "duracloud.vault.workdir";
    private String[] duracloudEmailAddresses;
    private String duracloudUsername;
    private String duracloudPassword;
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
    public static File getWorkDir() {
        String rootDir =  System.getProperty(DURACLOUD_VAULT_WORKDIR_SYSTEM_PROPERTY);
        if(rootDir == null){
            throw new RuntimeException("Unable to locate  workdir directory because the "
                + DURACLOUD_VAULT_WORKDIR_SYSTEM_PROPERTY
                + " system property was not set.  Please specify a java command line parameter (e.g. -D"
                + DURACLOUD_VAULT_WORKDIR_SYSTEM_PROPERTY + "=/path/to/workdir)");
        }
        File file =  createDirectoryIfNotExists(rootDir);
        if(!file.exists()){
            file.mkdirs();
        }
        return file;
    }
    
    /**
     * @param path
     * @return
     */
    private static File createDirectoryIfNotExists(String path) {
        File wdir = new File(path);
        if(!wdir.exists()){
            if (!wdir.mkdirs()) {
                throw new RuntimeException("failed to initialize "
                    + path + ": directory could not be created.");
            }
        }
        
        if(!wdir.canWrite()){
            throw new RuntimeException(wdir.getAbsolutePath() + " must be writable.");
        }

        return wdir;
    }

    /**
     * @return
     */
    public static File getContentRootDir() {
        return createDirectoryIfNotExists(new File(getWorkDir(), "content").getAbsolutePath());
    }
}
