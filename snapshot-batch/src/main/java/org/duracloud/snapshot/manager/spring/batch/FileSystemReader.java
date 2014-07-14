/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.DirectoryWalker;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

/**
 * @author Daniel Bernstein
 *         Date: Jul 16, 2014
 */
public class FileSystemReader implements ItemReader<File>{
    
    private SimpleDirectoryWalker walker;
    public FileSystemReader(File rootDirectory){
        this.walker = new SimpleDirectoryWalker(rootDirectory);
        this.walker.start();
    }
    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemReader#read()
     */
    @Override
    public File read()
        throws Exception,
            UnexpectedInputException,
            ParseException,
            NonTransientResourceException {
        return this.walker.next();
    }
    
    private class SimpleDirectoryWalker extends DirectoryWalker<File> {
        private SynchronousQueue<File> queue = new SynchronousQueue<>();
        private File root;
        public SimpleDirectoryWalker(final File root){
            
            this.root = root;
        }
        
        /**
         * 
         */
        public void start() {
            Thread t = new Thread(new Runnable() {
                
                @Override
                public void run() {
                    try {
                        walk(root, queue);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            t.start();
        }
        
           /* (non-Javadoc)
         * @see org.apache.commons.io.DirectoryWalker#handleFile(java.io.File, int, java.util.Collection)
         */
        @Override
        protected void
            handleFile(File file, int depth, Collection<File> results)
                throws IOException {
            try {
                if(file.isFile())
                    queue.put(file);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        public File next() {
            try {
                return queue.poll(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            return null;
        }
    }

}
