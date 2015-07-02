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
public class UpdateHistoryJSONParam {
	private String history;
	private Boolean alternate;

	public Boolean isAlternate() {
		return alternate;
	}

	public void setAlternate(Boolean alternate) {
		this.alternate = alternate;
	}

	public String getHistory() {
		return history;
	}

	public void setHistory(String history) {
		this.history = history;
	}
}
