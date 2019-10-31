package com.sysu.lbc.dataStructure;

public class Task {
    private Integer WF_ID;

    private Integer taskId;

    private Double neededResource;

    public Task(Integer WF_ID, Integer taskId, Double neededResource) {
        super();
        this.WF_ID = WF_ID;
        this.taskId = taskId;
        this.neededResource = neededResource;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Task && ((Task) obj).WF_ID.intValue() == this.WF_ID.intValue()
				&& ((Task) obj).taskId.intValue() == this.taskId.intValue();
    }

	@Override
	public int hashCode() {
		return this.WF_ID + this.taskId;
	}

	public Integer getWF_ID() {
        return WF_ID;
    }

    public void setWF_ID(Integer wF_ID) {
        WF_ID = wF_ID;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer id) {
        this.taskId = id;
    }

    public Double getNeededResource() {
        return neededResource;
    }

    public void setNeededResource(Double neededResource) {
        this.neededResource = neededResource;
    }

}
