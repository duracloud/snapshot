/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;

/**
 * @author Daniel Bernstein
 *         Date: Jul 29, 2015
 */
public class ManifestFileHelper {

    public static final String MANIFEST_MD5_TEXT_FILE_NAME = "manifest-md5.txt";

    /**
     * @param writer
     * @param contentId
     * @param checksum
     * @throws IOException
     */
    public static void writeManifestEntry(Writer writer, 
                                   String contentId, 
                                   String checksum) throws IOException {
        writer.write(checksum + "  data/" + contentId + "\n");
    }
    
    public static ManifestEntry parseManifestEntry(String line)
        throws ParseException{
        
        String[] values = line.split("\\s");
        String checksum = values[0];
        String contentId = values[2].substring(values[2].indexOf("/")+1);
        return new ManifestEntry(checksum,contentId);
    }
    
}
