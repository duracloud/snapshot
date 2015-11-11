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
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.duracloud.common.collection.WriteOnlyStringSet;
import org.duracloud.snapshot.common.SnapshotServiceConstants;

/**
 * @author Daniel Bernstein
 *         Date: Jul 29, 2015
 */
public class ManifestFileHelper {

    public static final String MANIFEST_MD5_TEXT_FILE_NAME = 
        SnapshotServiceConstants.MANIFEST_MD5_TXT_FILE_NAME;
    private static final Pattern MANIFEST_LINE_PATTERN =
        Pattern.compile("(\\w*)[\\s^\\r^\\n]+data/(.*)");
   
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
        try{
            Matcher matcher = MANIFEST_LINE_PATTERN.matcher(line);
            matcher.find();
            String checksum = matcher.group(1);
            String contentId = matcher.group(2);
            return new ManifestEntry(checksum,contentId);
            
        }catch(Exception ex){
            throw new ParseException(
                MessageFormat.format("failed to parse \"{0}\": " +
                                     "does not match regex (\"{1}\")",
                                     line,
                                     MANIFEST_LINE_PATTERN.pattern()),
                                     0);
        }
    }
    
    /**
     * @param manifestFile a manifest file
     * @return a set based on the combined content id and checksum.
     */
    public static WriteOnlyStringSet loadManifestSetFromFile(File manifestFile) throws Exception{
        int count = 0;
        WriteOnlyStringSet manifestSet;
        try(
            BufferedReader breader =
                new BufferedReader(new FileReader(manifestFile))){
            while(breader.readLine() != null){
                count++;
            }
        }catch(Exception ex){
            throw new RuntimeException(ex);
        }

        manifestSet = new WriteOnlyStringSet(count);

        
        try(
            BufferedReader breader =
                new BufferedReader(new FileReader(manifestFile))){

            String line = null;
            while((line = breader.readLine()) != null){
                ManifestEntry entry =  ManifestFileHelper.parseManifestEntry(line);
                manifestSet.add(formatManifestSetString(entry.getContentId(), 
                                                             entry.getChecksum()));
            }

        }catch(Exception ex){
            throw new RuntimeException(ex);
        }
        
        
        return manifestSet;

    }
    
    public static String formatManifestSetString(String contentId, String checksum) {
        return new StringBuilder().append(contentId)
                                  .append(":")
                                  .append(checksum)
                                  .toString();
    }
    
}
