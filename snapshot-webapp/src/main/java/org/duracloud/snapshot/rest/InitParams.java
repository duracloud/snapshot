/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * A data object holding initialization parameters.
 * @author Daniel Bernstein
 *         Date: Feb 11, 2014
 */
@JsonSerialize
@JsonDeserialize
public class InitParams {
    private String originatorEmailAddress = null;
    private String[] adminEmailAddresses = null;
    private String awsAccessKey;
    private String awsSecretKey;
    private String databaseUser;
    private String databasePassword;
    private String databaseURL;
    /**
     * @return the originatorEmailAddress
     */
    public String getOriginatorEmailAddress() {
        return originatorEmailAddress;
    }
    /**
     * @param originatorEmailAddress the originatorEmailAddress to set
     */
    public void setOriginatorEmailAddress(String originatorEmailAddress) {
        this.originatorEmailAddress = originatorEmailAddress;
    }
    /**
     * @return the adminEmailAddresses
     */
    public String[] getAdminEmailAddresses() {
        return adminEmailAddresses;
    }
    /**
     * @param adminEmailAddresses the adminEmailAddresses to set
     */
    public void setAdminEmailAddresses(String[] adminEmailAddresses) {
        this.adminEmailAddresses = adminEmailAddresses;
    }
    /**
     * @return the awsAccessKey
     */
    public String getAwsAccessKey() {
        return awsAccessKey;
    }
    /**
     * @param awsAccessKey the awsAccessKey to set
     */
    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }
    /**
     * @return the awsSecretKey
     */
    public String getAwsSecretKey() {
        return awsSecretKey;
    }
    /**
     * @param awsSecretKey the awsSecretKey to set
     */
    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }
    /**
     * @return the databaseUser
     */
    public String getDatabaseUser() {
        return databaseUser;
    }
    /**
     * @param databaseUser the databaseUser to set
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }
    /**
     * @return the databasePassword
     */
    public String getDatabasePassword() {
        return databasePassword;
    }
    /**
     * @param databasePassword the databasePassword to set
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }
    /**
     * @return the databaseURL
     */
    public String getDatabaseURL() {
        return databaseURL;
    }
    /**
     * @param databaseURL the databaseURL to set
     */
    public void setDatabaseURL(String databaseURL) {
        this.databaseURL = databaseURL;
    }
}
