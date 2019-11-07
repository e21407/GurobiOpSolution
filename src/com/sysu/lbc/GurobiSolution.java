package com.sysu.lbc;

import com.sysu.lbc.dataStructure.*;
import com.sysu.lbc.tool.Tool;
import com.sysu.lbc.tool.WorkflowGenerator;
import gurobi.*;
import sun.lwawt.macosx.CSystemTray;

import java.util.*;

public class GurobiSolution {
    static final String PATH_INFO_FILE = "data/pathInfo2.txt";
    static final String NODE_INFO_FILE = "data/info_of_nodes.txt";
    static final String LINKS_INFO_FILE = "data/info_cap_links2.txt";
    static final String GUROBI_LOG_NAME = "solution.log";

    List<Workflow> workflows = new ArrayList<>();
    Map<Integer, String> paths = new HashMap<>();   //<pathId, pathContent>, such as <1, 1>2>3 >
    Map<Integer, Double> nodes = new HashMap<>();  //<nodeId, nodeCapacity>
    Map<String, Double> oneHopLinks = new HashMap<>(); //<oneHopLink, bandwidth>, such as <1_2, 50>

    List<XVar> xVars = new ArrayList<>();    //<w_s_v, var>
    List<YVar> yVars = new ArrayList<>();    //<w_p_s_s', var>

    GRBEnv env;
    GRBModel model;
    GRBQuadExpr nodeLoadInfo;
    GRBQuadExpr linkLoadInfo;

    public void prepare() throws GRBException {
        long starTime = System.currentTimeMillis();
        env = new GRBEnv(GUROBI_LOG_NAME);
        model = new GRBModel(env);
        prepareWorkflows();
        prepareNodes();
        preparePaths();
        prepareOneHopLinks();
        prepareXVar();
        prepareYVar();
        prepareConstraint();
        setObjective();
        long endTime = System.currentTimeMillis();
        long dur = endTime - starTime;
        System.out.println("准备耗时：" + (dur / 1000) + "s");

    }

    public void doOptimize() throws GRBException {
        model.update();
        model.optimize();
    }

    private void printResult() throws GRBException {
        System.out.println("Obj is: " + model.get(GRB.DoubleAttr.ObjVal));
        System.out.println("cCost is: " + nodeLoadInfo.getValue());
        System.out.println("rCost is:" + linkLoadInfo.getValue());
        for (XVar var : xVars) {
            if (var.xVar.get(GRB.DoubleAttr.X) > 0)
                System.out.println(var.getVarName());
        }
        System.out.println("==================");
        for (YVar var : yVars) {
            if (var.yVar.get(GRB.DoubleAttr.X) > 0)
                System.out.println(var.getVarName());
        }
    }


    private void setObjective() throws GRBException {
        double throughput = prepareThroughput();
        nodeLoadInfo = prepareExprNode();
        linkLoadInfo = prepareExprLink();
        GRBQuadExpr objective = new GRBQuadExpr();

        objective.addConstant(throughput);
        objective.add(nodeLoadInfo);
        objective.add(linkLoadInfo);

        model.setObjective(objective, GRB.MAXIMIZE);
        model.update();
    }

    // 计算吞吐量
    private double prepareThroughput() {
        double throughput = 0.0;
        for (Workflow wf : workflows) {
            ArrayList<Flow> flows = wf.getFlows();
            for (Flow f : flows) {
                throughput += f.getNeededBandwidth();
            }
        }
        return throughput;
    }

    // 检查并记录节点上的工作负载情况
    private GRBQuadExpr prepareExprNode() throws GRBException {
        Map<String, List<CostItem>> costItems = new HashMap<>();
        for (Map.Entry<Integer, Double> nodeEntry : nodes.entrySet()) {
            Integer nodeId = nodeEntry.getKey();
            Double capacity = nodeEntry.getValue();
            for (Map.Entry<String, GRBVar> xVarEntry : xVars.entrySet()) {
                String[] xVarKeyItems = xVarEntry.getKey().split("_");
                int assignNodeId = Integer.parseInt(xVarKeyItems[2]);
                if (nodeId != assignNodeId) {
                    continue;
                }
                int wfId = Integer.parseInt(xVarKeyItems[0]);
                int taskId = Integer.parseInt(xVarKeyItems[1]);
                Task task = getTaskFormWorkFlows(wfId, taskId);
                Double neededResource = task.getNeededResource();
                String nodeIdStr = String.valueOf(nodeId);
                List<CostItem> nodeCostItem = costItems.get(nodeIdStr);
                if (null == nodeCostItem) {
                    nodeCostItem = new ArrayList<>();
                    costItems.put(nodeIdStr, nodeCostItem);
                }
                nodeCostItem.add(new CostItem(xVarEntry.getValue(), neededResource, capacity));

            }
        }
        return getSumCost(costItems);
    }


    // 检查并记录每段one-hop link上的链路负载情况
    private GRBQuadExpr prepareExprLink() throws GRBException {
        Map<String, List<CostItem>> costItems = new HashMap<>();
        for (Map.Entry<String, Double> oneHopLinkEntry : oneHopLinks.entrySet()) {
            String linkId = oneHopLinkEntry.getKey();
            double bandwidthCapacity = oneHopLinkEntry.getValue();
            for (Map.Entry<String, GRBVar> yVarEntry : yVars.entrySet()) {
                String[] yVarKeyItem = yVarEntry.getKey().split("_");
                int pathId = Integer.valueOf(yVarKeyItem[1]);
                String pathContent = paths.get(pathId);
                if (!isPathContainOneHopLink(pathContent, linkId)) {
                    continue;
                }
                int wfId = Integer.parseInt(yVarKeyItem[0]);
                int currTaskId = Integer.parseInt(yVarKeyItem[2]);
                int succTaskId = Integer.parseInt(yVarKeyItem[3]);
                Flow flow = getFlowFromWorkflows(wfId, currTaskId, succTaskId);
                double neededBandwidth = flow.getNeededBandwidth();

                List<CostItem> linkCostItem = costItems.get(linkId);
                if (null == linkCostItem) {
                    linkCostItem = new ArrayList<>();
                    costItems.put(linkId, linkCostItem);
                }
                linkCostItem.add(new CostItem(yVarEntry.getValue(), neededBandwidth, bandwidthCapacity));
            }
        }
        return getSumCost(costItems);
    }

    private GRBQuadExpr getSumCost(Map<String, List<CostItem>> costItems) throws GRBException {
        GRBQuadExpr result = new GRBQuadExpr();
        int count1 = 0;
        for (Map.Entry<String, List<CostItem>> linkCostItems : costItems.entrySet()) {
            count1++;
            List<CostItem> itemList = linkCostItems.getValue();
            int count2 = 1;
            for (CostItem item1 : itemList) {
                for (CostItem item2 : itemList) {
                    double offerResource = item1.getOfferResource();
                    double needResource1 = item1.getNeedResource();
                    double needResource2 = item2.getNeedResource();
                    double coeff = -1 * needResource1 * needResource2 / Math.pow(offerResource, 2);
                    GRBVar var1 = item1.getVar();
                    GRBVar var2 = item2.getVar();
                    GRBQuadExpr expr = new GRBQuadExpr();
                    expr.addTerm(coeff, var1, var2);
                    result.add(expr);
                    System.out.println(count1 + ": " + count2++);
                }
            }
        }
        return result;
    }

    private Task getTaskFormWorkFlows(int wfId, int taskId) {
        for (Workflow wf : workflows) {
            if (wfId != wf.getWF_ID()) {
                continue;
            }
            Set<Task> tasks = wf.getTasks();
            for (Task task : tasks) {
                if (taskId == task.getTaskId()) {
                    return task;
                }
            }
        }
        return null;
    }

    private Flow getFlowFromWorkflows(int wfId, int currTaskId, int succTaskId) {
        Flow result = null;
        for (Workflow wf : workflows) {
            if (wfId != wf.getWF_ID()) {
                continue;
            }
            ArrayList<Flow> flows = wf.getFlows();
            for (Flow flow : flows) {
                if (currTaskId == flow.getCurrTask().getTaskId() && succTaskId == flow.getSuccTask().getTaskId()) {
                    return flow;
                }
            }
        }
        return null;
    }

    private boolean isPathContainOneHopLink(String pathContent, String oneHopLinkKey) {
        String[] pathNodes = pathContent.split(">");
        String[] oneHopLinkNodes = oneHopLinkKey.split("_");
        String nodeU = oneHopLinkNodes[0];
        String nodeV = oneHopLinkNodes[1];
        for (int i = 0; i <= pathNodes.length - 2; i++) {
            String pathNodeU = pathNodes[i];
            String pathNodeV = pathNodes[i + 1];
            if (nodeU.equals(pathNodeU) && nodeV.equals(pathNodeV) || nodeV.equals(pathNodeU) && nodeU.equals(pathNodeV)) {
                return true;
            }
        }
        return false;
    }


    private void prepareConstraint() throws GRBException {
        // 每个任务只能放置在一个节点上
        addAssignmentConstraint(groupXVar(xVars));
        // 每个任务对只能采用一条通讯路径
//        addAssignmentConstraint(groupYVar(yVars));
        // y^{w,p}_{s,s'} == x^w_{s,v} * x^w_{s',v}
        addLinkNodeConstraint();
    }

    // todo test
    private void addLinkNodeConstraint() throws GRBException {
        for (Map.Entry<String, GRBVar> yVarEntry : yVars.entrySet()) {
            String[] yVarKeyItems = yVarEntry.getKey().split("_");
            Integer pathId = Integer.valueOf(yVarKeyItems[1]);
            String pathContent = paths.get(pathId);
            String[] pathNodes = pathContent.split(">");
            String currTaskNodeId = pathNodes[0];
            String succTaskNodeId = pathNodes[pathNodes.length - 1];
            String currXVarKey = yVarKeyItems[0] + "_" + yVarKeyItems[2] + "_" + currTaskNodeId;
            String succXVarKey = yVarKeyItems[0] + "_" + yVarKeyItems[3] + "_" + succTaskNodeId;
            GRBVar currXVar = xVars.get(currXVarKey);
            GRBVar succXVar = xVars.get(succXVarKey);

            if (null == currXVar || null == succXVar) {
                continue;
            }
            GRBQuadExpr exper = new GRBQuadExpr();
            exper.addTerm(1.0, yVarEntry.getValue());
            exper.addTerm(-1.0, currXVar, succXVar);
            model.addQConstr(exper, GRB.EQUAL, 0.0, yVarEntry.getKey());
        }
    }

    private void addAssignmentConstraint(Map<String, List<GRBVar>> varMap) throws GRBException {
        Map<String, List<GRBVar>> groupedVar = varMap;
        for (Map.Entry<String, List<GRBVar>> varsEntry : groupedVar.entrySet()) {
            List<GRBVar> vars = varsEntry.getValue();
            GRBLinExpr exper = new GRBLinExpr();
            for (GRBVar var : vars) {
                exper.addTerm(1.0, var);
            }
            model.addConstr(exper, GRB.EQUAL, 1.0, varsEntry.getKey());
        }
    }

    // 将变量y^{w,p}_{s,s'}按w_s_s'进行分类
    private Map<String, List<GRBVar>> groupYVar(Map<String, GRBVar> xVars) {
        Map<String, List<GRBVar>> result = new HashMap<>();
        for (Map.Entry<String, GRBVar> varEntry : yVars.entrySet()) {
            String varName = varEntry.getKey();
            String[] s = varName.split("_");
            String varNameIndex = s[0] + "_" + s[2] + "_" + s[3];
            List<GRBVar> vars = result.get(varNameIndex);
            if (null == vars) {
                vars = new ArrayList<>();
                result.put(varNameIndex, vars);
            }
            vars.add(varEntry.getValue());
        }
        return result;
    }

    // 将变量x^w_{s,v}按w_s进行分类
    private Map<String, List<GRBVar>> groupXVar(Map<String, GRBVar> xVars) {
        Map<String, List<GRBVar>> result = new HashMap<>();
        for (Map.Entry<String, GRBVar> varEntry : xVars.entrySet()) {
            String varName = varEntry.getKey();
            String[] s = varName.split("_");
            String varNameIndex = s[0] + "_" + s[1];
            List<GRBVar> vars = result.get(varNameIndex);
            if (null == vars) {
                vars = new ArrayList<>();
                result.put(varNameIndex, vars);
            }
            vars.add(varEntry.getValue());

        }
        return result;
    }

    // 变量x^w_{s,v},属于工作流w的任务s是否放置在节点v上，
    // 变量的名称用"w_s_v"表示，
    // 起始任务分配节点固定；
    private void prepareXVar() throws GRBException {
        for (Workflow wf : workflows) {
            Integer wfId = wf.getWF_ID();
            Set<Task> tasks = wf.getTasks();
            for (Task task : tasks) {
                for (Map.Entry nodeEntry : nodes.entrySet()) {

                    String varName = wfId.toString() + "_" + taskId.toString() + "_" + nodeEntry.getKey().toString();
                    GRBVar xVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    xVars.put(varName, xVar);
                }
            }
        }
        model.update();
    }

    // 变量y^{w,p}_{s,s'}，属于工作流w的任务对(s,s')是否采用路径p进行通讯；
    // 变量的名称用"w_p_s_s'"表示；
    private void prepareYVar() throws GRBException {
        for (Workflow wf : workflows) {
            Integer wfId = wf.getWF_ID();
            ArrayList<Flow> flows = wf.getFlows();
            for (Flow flow : flows) {
                String currTaskId = flow.getCurrTask().getTaskId().toString();
                String succTaskId = flow.getSuccTask().getTaskId().toString();
                for (Map.Entry<Integer, String> pathEntry : paths.entrySet()) {
                    String pathId = pathEntry.getKey().toString();
                    String varName = wfId.toString() + "_" + pathId + "_" + currTaskId + "_" + succTaskId;
                    GRBVar yVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    yVars.put(varName, yVar);
                }
            }
        }
        model.update();
    }


    private void prepareOneHopLinks() {
        String stringFromFile = Tool.getStringFromFile(LINKS_INFO_FILE);
        String[] lines = stringFromFile.split("\n");
        for (String aline : lines) {
            if (aline.trim().equals("")) {
                continue;
            }
            String[] items = aline.split("\t");
            String oneHopLinkId = items[1] + "_" + items[3];
            Double capacity = Double.valueOf(items[5]);
            oneHopLinks.put(oneHopLinkId, capacity);
        }
    }

    private void prepareNodes() {
        String stringFromFile = Tool.getStringFromFile(NODE_INFO_FILE);
        String[] lines = stringFromFile.split("\n");
        for (String aline : lines) {
            if (aline.trim().equals("")) {
                continue;
            }
            String[] items = aline.split("\t");
            nodes.put(Integer.valueOf(items[1]), Double.valueOf(items[3]));
        }
    }

    private void preparePaths() {
        String stringFromFile = Tool.getStringFromFile(PATH_INFO_FILE);
        String[] lines = stringFromFile.split("\n");
        int pathIdx = 1;
        for (String aline : lines) {
            if (aline.trim().equals("")) {
                continue;
            }
            String[] items = aline.split("\t");
            if (items[1].equals(items[3])) {
                continue;
            }
            paths.put(pathIdx++, items[5]);
        }
    }

    private void prepareWorkflows() {
        int workflowTemplateIdx = 0;
        WorkflowGenerator workflowGenerator = WorkflowGenerator.getWorkflowGenerator();
        Workflow wf1 = workflowGenerator.generateAWorkflow_V2(workflowTemplateIdx);
        Workflow wf2 = workflowGenerator.generateAWorkflow_V2(workflowTemplateIdx);
        Workflow wf3 = workflowGenerator.generateAWorkflow_V2(workflowTemplateIdx);
        workflows.add(wf1);
//        workflows.add(wf2);
//        workflows.add(wf3);
    }
}