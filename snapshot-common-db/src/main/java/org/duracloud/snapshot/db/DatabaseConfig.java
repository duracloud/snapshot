/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db;

/**
 * @author Daniel Bernstein
 *         Date: Feb 12, 2014
 */
public class DatabaseConfig {
    private String url;
    private String username;
    private String password;
    private boolean clean = false;
    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }
    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        this.url = url;
    }
    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }
    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }
    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }
    /**
     * @return the clean
     */
    public boolean isClean() {
        return clean;
    }
    
    /**
     * Flag indicating that the database should be wiped clean on
     * initialization.
     * @param clean the clean to set
     */
    public void setClean(boolean clean) {
        this.clean = clean;
    }
}
