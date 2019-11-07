package com.sysu.lbc.dataStructure;

import gurobi.GRBVar;

public class XVar {
    public int workflowId;
    public int taskId;
    public int nodeId;
    public GRBVar xVar;

    public XVar(int workflowId, int taskId, int nodeId, GRBVar xVar) {
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.nodeId = nodeId;
        this.xVar = xVar;
    }

    public String getVarName(){
        return workflowId + "_" + taskId + "_" + nodeId;

    }
}
