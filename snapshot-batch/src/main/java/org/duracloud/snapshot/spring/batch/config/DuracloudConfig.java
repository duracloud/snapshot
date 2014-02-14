/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.config;

/**
 * @author Daniel Bernstein
 *         Date: Feb 14, 2014
 */
public class DuracloudConfig {
    private String username;
    private String password;
    
    /**
     * @param username
     * @param password
     */
    public DuracloudConfig(String username, String password) {
        super();
        this.username = username;
        this.password = password;
    }
    
    /**
     * 
     */
    public DuracloudConfig() {}

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }
}
