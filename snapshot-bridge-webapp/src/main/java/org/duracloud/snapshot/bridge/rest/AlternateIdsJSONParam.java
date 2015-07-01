/**
 * 
 */
package org.duracloud.snapshot.bridge.rest;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author gad
 *
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
