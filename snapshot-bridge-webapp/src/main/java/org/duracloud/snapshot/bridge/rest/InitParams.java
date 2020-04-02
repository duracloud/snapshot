/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A data object holding initialization parameters.
 *
 * @author Daniel Bernstein
 * Date: Feb 11, 2014
 */
@JsonSerialize
@JsonDeserialize
public class InitParams {
    private String originatorEmailAddress = null;
    private String[] duracloudEmailAddresses = null;
    private String[] targetStoreEmailAddresses = null;
    private String awsAccessKey;
    private String awsSecretKey;
    private String databaseUser;
    private String databasePassword;
    private String databaseURL;
    private boolean clean = false;
    private String duracloudUsername;
    private String duracloudPassword;
    private Integer finalizerPeriodMs;
    private Integer daysToExpireRestore;

    /**
     * @return the clean
     */
    public boolean isClean() {
        return clean;
    }

    /**
     * Flag indicating that the database should be wiped clean on
     * initialization.
     *
     * @param clean the clean to set
     */
    public void setClean(boolean clean) {
        this.clean = clean;
    }

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
     * @return the finalizerPeriodMs
     */
    public Integer getFinalizerPeriodMs() {
        return finalizerPeriodMs;
    }

    /**
     * @param finalizerPeriodMs the finalizerPeriodMs to set
     */
    public void setFinalizerPeriodMs(Integer finalizerPeriodMs) {
        this.finalizerPeriodMs = finalizerPeriodMs;
    }

    /**
     * @return the number of days after which restored content should expire
     */
    public Integer getDaysToExpireRestore() {
        return daysToExpireRestore;
    }

    /**
     * @param daysToExpireRestore the number of days after which restored
     *                            content should expire
     */
    public void setDaysToExpireRestore(Integer daysToExpireRestore) {
        this.daysToExpireRestore = daysToExpireRestore;
    }
}
