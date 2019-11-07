package com.sysu.lbc.dataStructure;

public class Flow {
	public Task currTask;
	public Task succTask;
	public Double neededBandwidth;
	
	public Flow(Task currTask, Task succTask, Double neededBandwidth) {
		super();
		this.currTask = currTask;
		this.succTask = succTask;
		this.neededBandwidth = neededBandwidth;
	}
}
