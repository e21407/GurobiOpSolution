package com.sysu.lbc.dataStructure;

import gurobi.GRBVar;

public class YVar {
    public int workflowId;
    public int pathId;
    public int currTaskId;
    public int succTaskId;
    public GRBVar yVar;

    public YVar(int workflowId, int pathId, int currTaskId, int succTaskId, GRBVar yVar) {
        this.workflowId = workflowId;
        this.pathId = pathId;
        this.currTaskId = currTaskId;
        this.succTaskId = succTaskId;
        this.yVar = yVar;
    }

    public String getVarName() {
        return workflowId + "_" + pathId + "_" + currTaskId + "_" + succTaskId;

    }

}
