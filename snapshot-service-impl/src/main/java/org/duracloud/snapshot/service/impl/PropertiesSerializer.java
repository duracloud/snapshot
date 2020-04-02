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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
public class PropertiesSerializer {
    private static ObjectMapper mapper = new ObjectMapper();
    private static TypeReference<HashMap<String, String>> mapTypeRef =
        new TypeReference<HashMap<String, String>>() {
        };

    private PropertiesSerializer() {
        // Ensures no instances are made of this class, as there are only static members.
    }

    public static String serialize(Map<String, String> props) {
        try {
            return mapper.writeValueAsString(props);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Map<String, String> deserialize(String src) {
        HashMap<String, String> props;

        try {
            props =
                new ObjectMapper().readValue(src, mapTypeRef);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return props;

    }

}
