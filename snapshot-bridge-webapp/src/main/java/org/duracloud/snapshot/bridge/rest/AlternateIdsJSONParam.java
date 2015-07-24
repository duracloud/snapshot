/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author Gad Krumholz
 *         Date: May 19, 2015
 */
@JsonSerialize
@JsonDeserialize
public class AlternateIdsJSONParam {
	List<String> alternateIds;

	public List<String> getAlternateIds() {
		return alternateIds;
	}

	public void setAlternateIds(List<String> alternateIds) {
		this.alternateIds = alternateIds;
	}
}
