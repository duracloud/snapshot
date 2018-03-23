/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 * Date: Jul 25, 2014
 */
@Component
public class BridgeConfiguration {

    public static final String DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY = "duracloud.bridge.root.dir";
    public static final String DURACLOUD_BRIDGE_THREADS_PER_JOB = "duracloud.bridge.threads-per-job";

    private String[] duracloudEmailAddresses;
    private String duracloudUsername;
    private String duracloudPassword;

    private static Logger log = LoggerFactory.getLogger(BridgeConfiguration.class);

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
     * @return the bridge root dir.
     */
    public static File getBridgeRootDir() {
        String rootDir = System.getProperty(DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY);
        if (rootDir == null) {
            String error = "Unable to locate  bridge root directory because the " +
                           DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY
                           + " system property was not set.  Please specify a java command line parameter (e.g. -D"
                           + DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY + "=/path/to/root/dir)";
            throw new RuntimeException(error);
        }
        File file = createDirectoryIfNotExists(rootDir);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    private static File createDirectoryIfNotExists(String path) {
        File wdir = new File(path);
        if (!wdir.exists()) {
            if (!wdir.mkdirs()) {
                throw new RuntimeException("failed to initialize "
                                           + path + ": directory could not be created.");
            }
        }

        if (!wdir.canWrite()) {
            throw new RuntimeException(wdir.getAbsolutePath() + " must be writable.");
        }

        return wdir;
    }

    /**
     * @return a file representing the content root directory
     */
    public static File getContentRootDir() {
        return createDirectoryIfNotExists(new File(getBridgeRootDir(), "content").getAbsolutePath());
    }

    /**
     * @return a file representing the bridge work dir
     */
    public static File getBridgeWorkDir() {
        return createDirectoryIfNotExists(new File(getBridgeRootDir(), "work").getAbsolutePath());
    }

    public static int getBridgeThreadsPerJob() {
        String threadsPerJobConfig = System.getProperty(DURACLOUD_BRIDGE_THREADS_PER_JOB);
        if (null != threadsPerJobConfig) {
            try {
                return Integer.parseInt(threadsPerJobConfig);
            } catch (NumberFormatException e) {
                log.warn("Could not parse system property " +
                         DURACLOUD_BRIDGE_THREADS_PER_JOB +
                         " with value " + threadsPerJobConfig +
                         " into an int. Proceeding with default threads setting.");
            }
        }

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (availableProcessors > 1) {
            return availableProcessors - 1;
        } else {
            return 1;
        }
    }

}
