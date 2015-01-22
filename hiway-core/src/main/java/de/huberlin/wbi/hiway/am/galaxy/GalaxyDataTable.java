/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Marc Bux (HU Berlin)
 * Jörgen Brandt (HU Berlin)
 * Hannes Schuh (HU Berlin)
 * Ulf Leser (HU Berlin)
 *
 * Jörgen Brandt is funded by the European Commission through the BiobankCloud
 * project. Marc Bux is funded by the Deutsche Forschungsgemeinschaft through
 * research training group SOAMED (GRK 1651).
 *
 * Copyright 2014 Humboldt-Universität zu Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.huberlin.wbi.hiway.am.galaxy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

public class GalaxyDataTable {
	private final String[] columns;
	private final String comment_char;
	private Map<String, Map<String, String>> contentByValue;
	private final String name;
	private final String path;

	public GalaxyDataTable(String name, String comment_char, String[] columns, String path) {
		this.name = name;
		this.comment_char = comment_char;
		this.columns = columns;
		this.path = path;
		contentByValue = new HashMap<>();
	}

	public void addContent(String[] content) {
		Map<String, String> newContent = new HashMap<>();
		for (int i = 0; i < columns.length; i++) {
			newContent.put(columns[i], content[i]);
			if (columns[i].equals("value"))
				contentByValue.put(content[i], newContent);
		}
	}

	public String[] getColumns() {
		return columns;
	}

	public String getComment_char() {
		return comment_char;
	}

	public JSONObject getContent(String value) {
		JSONObject inner = new JSONObject(contentByValue.get(value));
		JSONObject outer = new JSONObject();
		try {
			outer.put("fields", inner);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return outer;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public Set<String> getValues() {
		return contentByValue.keySet();
	}
}