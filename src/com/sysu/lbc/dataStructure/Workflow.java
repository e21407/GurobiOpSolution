package com.sysu.lbc.dataStructure;
/**
 * 将代码改成适应动态情况时候新增的类，给工作流新增了一个时间属性描述工作流执行的具体时长
 * @author liubaichuan
 * @since 2018-10-11
 *
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Workflow {

	private Integer WF_ID;

	/** 工作流持续的时长 */
	private Double duration;

	private ArrayList<Flow> flows;

	public Workflow(Integer wF_ID, Double duration, ArrayList<Flow> flows) {
		super();
		WF_ID = wF_ID;
		this.duration = duration;
		if (flows == null) {
			this.flows = new ArrayList<Flow>();
		} else {
			this.flows = flows;
		}
	}

	public boolean addFlow(Flow f) {
		if (f == null)
			return false;
		flows.add(f);
		return true;
	}

	public Set<Task> getTasks(){
		Set<Task> result = new HashSet<>();
		for(Flow flows : flows){
			result.add(flows.getCurrTask());
			result.add(flows.getSuccTask());
		}
		return result;
	}

	public Integer getWF_ID() {
		return WF_ID;
	}

	public void setWF_ID(Integer wF_ID) {
		WF_ID = wF_ID;
	}

	public Double getDuration() {
		return duration;
	}

	public void setDuration(Double duration) {
		this.duration = duration;
	}

	public ArrayList<Flow> getFlows() {
		return flows;
	}

	public void setFlows(ArrayList<Flow> flows) {
		this.flows = flows;
	}

}
