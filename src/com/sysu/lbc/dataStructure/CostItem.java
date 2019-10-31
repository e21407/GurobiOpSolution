package com.sysu.lbc.dataStructure;

import gurobi.GRBVar;

public class CostItem {
    GRBVar var;
    double needResource;
    double offerResource;

    public CostItem(GRBVar yVar, double needBandwidth, double linkBandwidth) {
        this.var = yVar;
        this.needResource = needBandwidth;
        this.offerResource = linkBandwidth;
    }

    public GRBVar getVar() {
        return var;
    }

    public void setVar(GRBVar var) {
        this.var = var;
    }

    public double getNeedResource() {
        return needResource;
    }

    public void setNeedResource(double needResource) {
        this.needResource = needResource;
    }

    public double getOfferResource() {
        return offerResource;
    }

    public void setOfferResource(double offerResource) {
        this.offerResource = offerResource;
    }
}
