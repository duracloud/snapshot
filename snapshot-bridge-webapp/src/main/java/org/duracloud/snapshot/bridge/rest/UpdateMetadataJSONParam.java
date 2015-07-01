/**
 * 
 */
package org.duracloud.snapshot.bridge.rest;

import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author gad
 *
 */
@JsonSerialize
@JsonDeserialize
public class UpdateMetadataJSONParam {
	private String metadata;
	private Boolean isAlternate;

	public Boolean getIsAlternate() {
		return isAlternate;
	}

	public void setIsAlternate(Boolean isAlternate) {
		this.isAlternate = isAlternate;
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}
}
