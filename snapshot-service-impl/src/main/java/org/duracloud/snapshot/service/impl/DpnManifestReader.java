/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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

    private File manifestFile;
    private BufferedReader reader;
    /**
     * @param md5Manifest
     */
     public DpnManifestReader(File manifestFile) {
         this.manifestFile = manifestFile;
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemReader#read()
     */
    @Override
    public synchronized ManifestEntry read()
        throws Exception,
            UnexpectedInputException,
            ParseException,
            NonTransientResourceException {
        if(this.reader == null){
            this.reader = new BufferedReader(new FileReader(manifestFile));
        }
        
        String line = this.reader.readLine();
        if(line != null){
            return ManifestFileHelper.parseManifestEntry(line);
        }else{
            return null;
        }
    }
    
}
