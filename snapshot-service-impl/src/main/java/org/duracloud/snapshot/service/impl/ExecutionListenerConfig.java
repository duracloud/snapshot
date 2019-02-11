/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bill Branan
 * Date: 2/13/14
 */
public class ExecutionListenerConfig {

    private String sesUsername;
    private String sesPassword;
    private String originatorEmailAddress;
    private String[] duracloudEmailAddresses;
    private String[] targetStoreEmailAddresses;
    private File contentRoot;

    public String getSesUsername() {
        return sesUsername;
    }

    public void setSesUsername(String sesUsername) {
        this.sesUsername = sesUsername;
    }

    public String getSesPassword() {
        return sesPassword;
    }

    public void setSesPassword(String sesPassword) {
        this.sesPassword = sesPassword;
    }

    public String getOriginatorEmailAddress() {
        return originatorEmailAddress;
    }

    public void setOriginatorEmailAddress(String originatorEmailAddress) {
        this.originatorEmailAddress = originatorEmailAddress;
    }

    public String[] getDuracloudEmailAddresses() {
        return duracloudEmailAddresses;
    }

    public void setDuracloudEmailAddresses(String[] duracloudEmailAddresses) {
        this.duracloudEmailAddresses = duracloudEmailAddresses;
    }

    public String[] getTargetStoreEmailAddresses() {
        return targetStoreEmailAddresses;
    }

    public void setTargetStoreEmailAddresses(String[] targetStoreEmailAddresses) {
        this.targetStoreEmailAddresses = targetStoreEmailAddresses;
    }

    public String[] getAllEmailAddresses() {
        List<String> allAddresses = new ArrayList<String>();
        allAddresses.addAll(Arrays.asList(duracloudEmailAddresses));
        allAddresses.addAll(Arrays.asList(targetStoreEmailAddresses));
        return allAddresses.toArray(new String[allAddresses.size()]);
    }

    /**
     * @param contentRoot the contentRoot to set
     */
    public void setContentRoot(File contentRoot) {
        this.contentRoot = contentRoot;
    }

    /**
     * @return the content root directory
     */
    public File getContentRoot() {
        return contentRoot;
    }

}
