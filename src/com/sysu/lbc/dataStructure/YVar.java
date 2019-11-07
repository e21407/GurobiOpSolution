package com.sysu.lbc.dataStructure;

import gurobi.GRBVar;

public class YVar extends  Var{
    public int workflowId;
    public int pathId;
    public int currTaskId;
    public int succTaskId;

    public YVar(int workflowId, int pathId, int currTaskId, int succTaskId, GRBVar yVar) {
        this.workflowId = workflowId;
        this.pathId = pathId;
        this.currTaskId = currTaskId;
        this.succTaskId = succTaskId;
        super.var = yVar;
    }

    public String getVarName() {
        return workflowId + "_" + pathId + "_" + currTaskId + "_" + succTaskId;

    }

}
