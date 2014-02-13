/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bill Branan
 *         Date: 2/13/14
 */
public class SnapshotNotifyConfig {

    private String sesUsername;
    private String sesPassword;
    private String originatorEmailAddress;
    private String[] duracloudEmailAddresses;
    private String[] dpnEmailAddresses;

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

    public String[] getDpnEmailAddresses() {
        return dpnEmailAddresses;
    }

    public void setDpnEmailAddresses(String[] dpnEmailAddresses) {
        this.dpnEmailAddresses = dpnEmailAddresses;
    }

    public String[] getAllEmailAddresses() {
        List<String> allAddresses = new ArrayList<String>();
        allAddresses.addAll(Arrays.asList(duracloudEmailAddresses));
        allAddresses.addAll(Arrays.asList(dpnEmailAddresses));
        return allAddresses.toArray(new String[allAddresses.size()]);
    }

}
