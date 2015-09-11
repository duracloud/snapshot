/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.duracloud.client.ContentStore;
import org.duracloud.common.collection.WriteOnlyStringSet;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.constant.ManifestFormat;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.manifest.ManifestFormatter;
import org.duracloud.manifest.impl.TsvManifestFormatter;
import org.duracloud.manifeststitch.StitchedManifestGenerator;
import org.duracloud.mill.db.model.ManifestItem;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

/**
 * This class is responsible for reading the contents and properties of a duracloud content item,
 * writing it to disk,  appending its md5 and sha256 to separate text files, appending
 * the item properties to a json file, and writing the item to the snapshot content repo.
 * 
 * @author Erik Paulsson
 *         Date: 2/7/14
 */
public class SpaceItemWriter implements ItemWriter<ContentItem>,
                                        StepExecutionListener,
                                        ItemWriteListener<ContentItem> {

    private static final Logger log =
        LoggerFactory.getLogger(SpaceItemWriter.class);

    private RetrievalSource retrievalSource;
    private File contentDir;
    private OutputWriter outputWriter;
    private File md5Manifest; 
    private BufferedWriter propsWriter;
    private BufferedWriter md5Writer;
    private BufferedWriter sha256Writer;
    private ContentStore contentStore;
    private String spaceId;
    private ChecksumUtil sha256ChecksumUtil;
    private ContentItem snapshotPropsContentItem;
    private SnapshotManager snapshotManager;
    private Snapshot snapshot;
    private List<String> errors = new LinkedList<String>();
    
    public SpaceItemWriter(Snapshot snapshot, RetrievalSource retrievalSource,
                           File contentDir,
                           OutputWriter outputWriter,
                           BufferedWriter propsWriter,
                           BufferedWriter md5Writer,
                           File md5Manifest,
                           BufferedWriter sha256Writer, 
                           SnapshotManager snapshotManager, 
                           ContentStore contentStore,
                           String spaceId) {
        this.snapshot = snapshot;
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
        this.propsWriter = propsWriter;
        this.md5Writer = md5Writer;
        this.md5Manifest = md5Manifest;
        this.sha256Writer = sha256Writer;
        this.sha256ChecksumUtil =
            new ChecksumUtil(ChecksumUtil.Algorithm.SHA_256);
        this.snapshotManager = snapshotManager;
        this.contentStore = contentStore;
        this.spaceId = spaceId;
    }

    @Override
    public void write(List<? extends ContentItem> items) throws IOException {
        for(ContentItem contentItem: items) {
            String contentId = contentItem.getContentId();
            log.debug("writing: {}", contentId);

            if(!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                File dataDir = getDataDir();
                retrieveFile(contentItem, dataDir);
            } else {
                // Cache the snapshot properties ContentItem so we can
                // retrieve it last in the 'afterStep' method.
                snapshotPropsContentItem = contentItem;
            }
        }
    }

    /**
     * @return
     */
    private File getDataDir() {
        return new File(contentDir, "data");
    }

    protected void retrieveFile(ContentItem contentItem, File directory)
            throws IOException {
        retrieveFile(contentItem, directory, true, false);
    }

    protected void retrieveFile(ContentItem contentItem, File directory,
                                boolean writeChecksums, boolean lastItem)
            throws IOException {
        RetrievalWorker retrievalWorker =
            new RetrievalWorker(contentItem, retrievalSource, directory,
                                true, outputWriter, false, true);
        Map<String,String> props = retrievalWorker.retrieveFile();
        File localFile = retrievalWorker.getLocalFile();

        String md5Checksum = props.get(ContentStore.CONTENT_CHECKSUM);
        log.info("Retrieved item {} from space {} with MD5 checksum {}",
                 contentItem.getContentId(),
                 contentItem.getSpaceId(),
                 md5Checksum);

        if(localFile.exists() && md5Checksum != null) {
            if(writeChecksums) {
                writeMD5Checksum(contentItem, md5Checksum);
                writeSHA256Checksum(contentItem, localFile);
            }
            writeToSnapshotManager(contentItem, props);
            writeContentProperties(contentItem, props, lastItem);
        } else {
            // There was a problem! Throw a meaningful exception:
            String baseError =
                String.format("Retrieved item {} from space {} could not " +
                              "be processed due to: ",
                              contentItem.getContentId(),
                              contentItem.getSpaceId());
            if(!localFile.exists()) {
                throw new IOException(baseError + "The local file at path " +
                                      localFile.getAbsolutePath()+
                                      " could not be found.");
            } else {
                throw new IOException(baseError + "MD5 checksum for " +
                                      "retrieved file was null");
            }
        }
    }

    /**
     * @param contentItem
     * @param props
     */
    private void writeToSnapshotManager(final ContentItem contentItem,
                                        final Map<String, String> props) throws IOException{
        try {
            new Retrier().execute(new Retriable() {
                @Override
                public Object retry() throws Exception {
                    snapshotManager.addContentItem(snapshot, contentItem.getContentId(), props);
                    return null;
                }

            });
        } catch (Exception e) {
            log.error("failed to add snapshot content item: "
                + contentItem + ": " + e.getMessage(), e);
            throw new IOException(e);
        }
    }

    protected void writeMD5Checksum(ContentItem contentItem,
                                    String md5Checksum) throws IOException {
        synchronized (md5Writer) {
            ManifestFileHelper.writeManifestEntry(md5Writer, 
                                                    contentItem.getContentId(), 
                                                    md5Checksum);
        }
    }



    protected synchronized void writeSHA256Checksum(ContentItem contentItem,
                                       File localFile) throws IOException {
        String sha256Checksum = sha256ChecksumUtil.generateChecksum(localFile);
        ManifestFileHelper.writeManifestEntry(sha256Writer, 
                                                contentItem.getContentId(), 
                                                sha256Checksum);
    }

    protected void writeContentProperties(ContentItem contentItem,
                                          Map<String,String> props,
                                          boolean lastItem)
            throws IOException {
        
        
        Set<String> propKeys = props.keySet();
        StringBuffer sb = new StringBuffer(100);
        sb.append("{\n  \"" + contentItem.getContentId() + "\": {\n");
        for(String propKey: propKeys) {
            sb.append("    \"" + propKey + "\": \"" + props.get(propKey) + "\",\n");
        }
        sb.deleteCharAt(sb.length() - 2); // delete comma after last key/value pair

        sb.append("  }\n}");
        if(! lastItem) {
            sb.append(",");
        }
        sb.append("\n");

        synchronized (propsWriter) {
            propsWriter.write(sb.toString());
        }
    }

    protected void retrieveSnapshotProperties() {
        if(snapshotPropsContentItem != null) {
            try {
                retrieveFile(snapshotPropsContentItem, contentDir, false, true);
            } catch (IOException ioe) {
                log.error("Error retrieving the snapshot properties file: ",
                             ioe);
            }
        } else {
            String message = "No snapshot properties file found. (" +
                Constants.SNAPSHOT_PROPS_FILENAME + ")";
            log.error(message);
            errors.add(message);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.debug("Step complete with status: {}",
                     stepExecution.getExitStatus());
        try {
            md5Writer.close();
        } catch (IOException ioe) {
            String message = "Error closing MD5 manifest BufferedWriter: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);
        }

        try {
            sha256Writer.close();
        } catch (IOException ioe) {
            String message = "Error closing SHA-256 manifest BufferedWriter: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);

        }

        retrieveSnapshotProperties();
        try {
            synchronized (propsWriter) {
                propsWriter.write("]\n");
            }

            propsWriter.close();

        } catch (IOException ioe) {
            String message = "Error writing end of content property manifest: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);
        }
        
        if(errors.size() == 0){
            verifySpaceManifestAgainstDpnManifest();
        }
        
        ExitStatus status = stepExecution.getExitStatus();
        
        if(errors.size() > 0){
            status = status.and(ExitStatus.FAILED);
            for(String error : errors){
                status = status.addExitDescription(error);
            }
        }
        return status;
    }

    /**
     * 
     */
    private void verifySpaceManifestAgainstDpnManifest() {

        try {
            WriteOnlyStringSet dpnManifest = ManifestFileHelper.loadManifestSetFromFile(this.md5Manifest);
            StitchedManifestGenerator generator = new StitchedManifestGenerator(contentStore);
           
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(generator.generate(spaceId,
                                                                            ManifestFormat.TSV)));
            ManifestFormatter formatter = new TsvManifestFormatter();
            //skip header
            if(formatter.getHeader() != null){
                reader.readLine();
            }

            String line = null;
            int stitchedManifestCount = 0;
            while((line = reader.readLine()) != null){
                ManifestItem item = formatter.parseLine(line);
                String contentId = item.getContentId();
                if(!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)){
                    if(!dpnManifest.contains(ManifestFileHelper.formatManifestSetString(contentId, item.getContentChecksum()))){
                        errors.add("DPN manifest does not contain content id/checksum combination ("
                            + contentId + ", " + item.getContentChecksum());
                    }
                    stitchedManifestCount++;
                }
            }
            
            int dpnCount = dpnManifest.size();
            if(stitchedManifestCount != dpnCount){
                errors.add("DPN Manifest size ("
                    + dpnCount + ") does not equal DuraCloud Manifest (" + stitchedManifestCount + ")");
            }
            
        } catch (Exception e) {
            String message = "Failed to verify space manifest against dpn manifest:" + e.getMessage();
            errors.add(message);
            log.error(message, e);
            throw new RuntimeException(e);
        }


    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
            errors.clear();
            synchronized (propsWriter) {
                propsWriter.write("[\n");
            }
        } catch (IOException ioe) {
            log.error("Error writing start of content property " +
                             "manifest: ", ioe);
        }
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void onWriteError(Exception e, List<? extends ContentItem> items) {
        StringBuilder sb = new StringBuilder();
        for(ContentItem item: items) {
            sb.append(item.getContentId() + ", ");
        }
        
        String message = "Error writing item(s): " + e.getMessage() + ": items=" + sb.toString();
        this.errors.add(message);
        log.error(message,e);
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void beforeWrite(List<? extends ContentItem> items) {
        // no-op impl
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void afterWrite(List<? extends ContentItem> items) {
        // no-op impl
    }
}
