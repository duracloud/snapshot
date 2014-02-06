/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.springframework.batch.item.ItemWriter;

import java.io.File;
import java.util.List;

/**
 * @author Erik Paulsson
 *         Date: 2/3/14
 */
public class SpaceItemWriter implements ItemWriter<File> {

    public void write(List<? extends File> items) {
        for(File file: items) {
            System.out.println("SpaceItemWriter#write: " + file.getAbsolutePath());
        }
    }
}
