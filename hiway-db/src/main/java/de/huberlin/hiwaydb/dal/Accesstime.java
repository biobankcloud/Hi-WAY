/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Hannes Schuh (HU Berlin)
 * Marc Bux (HU Berlin)
 * Jörgen Brandt (HU Berlin)
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
package de.huberlin.hiwaydb.dal;

//Generated 19.05.2014 12:56:25 by Hibernate Tools 3.4.0.CR1

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Accesstime generated by hbm2java
 */
@Entity
public class Accesstime {

	@Id
	@GeneratedValue
	private Long id;

	private String funktion;
	private String input;
	private Long tick;
	private Long tock;
	private Long ticktockdif;
	private Long dbvolume;
	private Long returnvolume;
	private String wfName;
	private String runId;
	private String keyinput;
	private String config;

	public Accesstime() {
	}

	public String getFunktion() {
		return funktion;
	}

	public void setFunktion(String funktion) {
		this.funktion = funktion;
	}

	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public Long getTick() {
		return tick;
	}

	public void setTick(Long tick) {
		this.tick = tick;
	}

	public Long getTock() {
		return tock;
	}

	public void setTock(Long tock) {
		this.tock = tock;
	}

	public Long getDbvolume() {
		return dbvolume;
	}

	public void setDbvolume(Long dbvolume) {
		this.dbvolume = dbvolume;
	}

	public Long getReturnvolume() {
		return returnvolume;
	}

	public void setReturnvolume(Long returnvolume) {
		this.returnvolume = returnvolume;
	}

	public Long getTicktockdif() {
		return ticktockdif;
	}

	public void setTicktockdif(Long ticktockdif) {
		this.ticktockdif = ticktockdif;
	}

	public String getWfName() {
		return wfName;
	}

	public void setWfName(String wfName) {
		this.wfName = wfName;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public String getConfig() {
		return config;
	}

	public void setConfig(String config) {
		this.config = config;
	}

	public String getKeyinput() {
		return keyinput;
	}

	public void setKeyinput(String keyinput) {
		this.keyinput = keyinput;
	}

}
