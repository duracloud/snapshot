/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.config;

import java.io.File;

/**
 * @author Daniel Bernstein
 *         Date: Feb 14, 2014
 */
public class SnapshotJobManagerConfig {
    private String duracloudUsername;
    private String duracloudPassword;
    private File contentRootDir;
    private File workDir;

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

    /**
     * @param username the username to set
     */
    public void setDuracloudUsername(String username) {
        this.duracloudUsername = username;
    }

    /**
     * @return the username
     */
    public String getDuracloudUsername() {
        return duracloudUsername;
    }
    
    /**
     * @param password the password to set
     */
    public void setDuracloudPassword(String password) {
        this.duracloudPassword = password;
    }

    /**
     * @return the password
     */
    public String getDuracloudPassword() {
        return duracloudPassword;
    }

    /**
     * @return the workDir
     */
    public File getWorkDir() {
        return workDir;
    }

    /**
     * @param workDir the workDir to set
     */
    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }
}
