/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

/**
 * A simple entity class for holding generic response data returned
 * from rest calls.
 *
 * @author Daniel Bernstein
 * Date: Feb 11, 2014
 */
public class ResponseDetails {
    private String message;

    /**
     *
     */
    public ResponseDetails() {
    }

    /**
     * @param message
     */
    public ResponseDetails(String message) {
        setMessage(message);
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
