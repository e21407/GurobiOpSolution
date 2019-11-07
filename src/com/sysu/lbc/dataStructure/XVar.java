package com.sysu.lbc.dataStructure;

import gurobi.GRBVar;

public class XVar extends Var {
    public int workflowId;
    public int taskId;
    public int nodeId;

    public XVar(int workflowId, int taskId, int nodeId, GRBVar xVar) {
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.nodeId = nodeId;
        super.var = xVar;
    }

    public String getVarName(){
        return workflowId + "_" + taskId + "_" + nodeId;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof XVar && ((XVar) obj).workflowId == this.workflowId
                && ((XVar) obj).taskId == this.taskId && ((XVar) obj).nodeId == this.nodeId;
    }
}
