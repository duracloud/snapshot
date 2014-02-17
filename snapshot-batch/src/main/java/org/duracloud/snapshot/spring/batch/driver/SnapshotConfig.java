/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.driver;

import java.io.File;

/**
 * @author Erik Paulsson
 *         Date: 2/7/14
 */
public class SnapshotConfig {

    private static final String DEFAULT_CONTEXT = "durastore";
    private String host;
    private int port;
    private String context = DEFAULT_CONTEXT;
    private String storeId;
    private String snapshotId;
    private String space;
    private File contentDir;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public File getContentDir() {
        return contentDir;
    }

    public void setContentDir(File contentDir) {
        this.contentDir = contentDir;
    }

}
