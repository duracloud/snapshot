/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest.config;

import org.duracloud.common.model.Credential;

/**
 * @author Bill Branan
 *         Date: July 14, 2016
 */
public class RootUserCredential extends Credential {

    private static final String usernameProp = "duracloud.bridge.root.username";
    private static final String passwordProp = "duracloud.bridge.root.password";

    private static final String defaultUsername = "root";
    private static final String defaultPassword = "rpw";

    public RootUserCredential() {
        super(getRootUsername(), getRootPassword());
    }

    public static String getRootUsername() {
        return getProperty(usernameProp, defaultUsername);
    }

    private static String getRootPassword() {
        return getProperty(passwordProp, defaultPassword);
    }

    private static String getProperty(String propertyKey,
                                      String defaultValue) {
        return System.getProperty(propertyKey, defaultValue);
    }

}