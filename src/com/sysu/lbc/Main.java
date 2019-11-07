package com.sysu.lbc;

import gurobi.GRBException;

public class Main {

    public static void main(String[] args) throws GRBException {
        GurobiSolution solution = new GurobiSolution();
        solution.prepare();
        solution.doOptimize();
        solution.printResult();
    }
}
