/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author Gad Krumholz
 *         Date: May 19, 2015
 */
@JsonSerialize
@JsonDeserialize
public class UpdateHistoryJSONParam {
	private String history;
	private Boolean alternate;

	public Boolean getAlternate() {
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
