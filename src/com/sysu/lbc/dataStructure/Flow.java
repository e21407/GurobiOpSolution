package com.sysu.lbc.dataStructure;

public class Flow {
	private Task currTask;
	private Task SuccTask;
	private Double neededBandwidth;
	
	public Flow(Task currTask, Task succTask, Double neededBandwidth) {
		super();
		this.currTask = currTask;
		SuccTask = succTask;
		this.neededBandwidth = neededBandwidth;
	}

	public Task getCurrTask() {
		return currTask;
	}

	public void setCurrTask(Task currTask) {
		this.currTask = currTask;
	}

	public Task getSuccTask() {
		return SuccTask;
	}

	public void setSuccTask(Task succTask) {
		SuccTask = succTask;
	}

	public Double getNeededBandwidth() {
		return neededBandwidth;
	}

	public void setNeededBandwidth(Double neededBandwidth) {
		this.neededBandwidth = neededBandwidth;
	}
	
	
	
}
