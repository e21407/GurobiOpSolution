package com.sysu.lbc.dataStructure;

public class Task {
    public Integer workflowId;
    public Integer taskId;
    public Double neededResource;

    public Task(Integer WF_ID, Integer taskId, Double neededResource) {
        super();
        this.workflowId = WF_ID;
        this.taskId = taskId;
        this.neededResource = neededResource;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Task && ((Task) obj).workflowId.intValue() == this.workflowId.intValue()
                && ((Task) obj).taskId.intValue() == this.taskId.intValue();
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + workflowId;
        result = 31 * result + taskId;
        return result;
    }

}