/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;

import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

/**
 * This class reads a DPN manifest 
 * @author Daniel Bernstein
 *         Date: Jul 28, 2015
 */
public class DpnManifestReader implements ItemReader<ManifestEntry> {

    /**
     * @param md5Manifest
     * @param md5
     */
     public DpnManifestReader(File md5Manifest, Algorithm md5) {
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemReader#read()
     */
    @Override
    public ManifestEntry read()
        throws Exception,
            UnexpectedInputException,
            ParseException,
            NonTransientResourceException {
        // TODO Auto-generated method stub
        return null;
    }

}
