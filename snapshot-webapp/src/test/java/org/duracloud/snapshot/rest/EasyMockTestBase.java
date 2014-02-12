/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.util.LinkedList;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A handy base class to simplify the writing of unit tests with easymock.
 * @author Daniel Bernstein
 *         Date: Feb 12, 2014
 */
public class EasyMockTestBase {
    private static Logger log = LoggerFactory.getLogger(EasyMockTestBase.class);
    private List<Object> mocks = new LinkedList<>();

    @Before
    public void setup() {
        mocks.clear();
    }

    protected <T> T createMock(Class<T> mockClass){
       return EasyMock.createMock(mockClass); 
    }
    
    protected void replay() {
        for(Object mock : mocks){
            EasyMock.replay(mock);
        }
    }
    
    @After
    public void teartDown(){
        log.debug("tearing down.");
        for(Object mock : mocks){
            EasyMock.verify(mock);
        }
    }
}
