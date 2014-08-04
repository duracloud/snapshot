/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Jul 31, 2014
 */
public class PropertiesSerializerTest {

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.PropertiesSerializer#serialize(java.util.Map)}.
     */
    @Test
    public void test() {
        Map<String,String> map = new HashMap<>();
        map.put("key", "value");
        String json = PropertiesSerializer.serialize(map);
        Map<String,String> map2 = PropertiesSerializer.deserialize(json);
        Assert.assertEquals(map, map2);
    }

   

}
