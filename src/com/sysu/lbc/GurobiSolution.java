package com.sysu.lbc;

import com.sysu.lbc.dataStructure.*;
import com.sysu.lbc.tool.Tool;
import com.sysu.lbc.tool.WorkflowGenerator;
import gurobi.*;

import java.util.*;

public class GurobiSolution {
    static final String PATH_INFO_FILE = "data/pathInfo2.txt";
    static final String NODE_INFO_FILE = "data/info_of_nodes.txt";
    static final String LINKS_INFO_FILE = "data/info_cap_links2.txt";
    static final String GUROBI_LOG_NAME = "solution.log";

    List<Workflow> workflows = new ArrayList<>();
    Map<Integer, String> paths = new HashMap<>();   //<pathId, pathContent>, such as <1, 1>2>3 >
    Map<Integer, Double> nodes = new HashMap<>();  //<nodeId, nodeCapacity>
    List<Link> links = new ArrayList<>();

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

    public void printResult() throws GRBException {
        System.out.println("Obj is: " + model.get(GRB.DoubleAttr.ObjVal));
        System.out.println("cCost is: " + nodeLoadInfo.getValue());
        System.out.println("rCost is:" + linkLoadInfo.getValue());
        for (XVar var : xVars) {
            if (var.var.get(GRB.DoubleAttr.X) > 0)
                System.out.println(var.getVarName());
        }
        System.out.println("==================");
        for (YVar var : yVars) {
            if (var.var.get(GRB.DoubleAttr.X) > 0)
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
                throughput += f.neededBandwidth;
            }
        }
        return throughput;
    }

    // 检查并记录节点上的工作负载情况
    private GRBQuadExpr prepareExprNode() throws GRBException {
        List<GRBLinExpr> loadExprOfEachNode = new ArrayList<>();
        for (Map.Entry<Integer, Double> nodeEntry : nodes.entrySet()) {
            int nodeId = nodeEntry.getKey();
            double capacity = nodeEntry.getValue();
            GRBLinExpr nodeLoadExpr = new GRBLinExpr();
            for (XVar x : xVars) {
                int assignNodeId = x.nodeId;
                if (nodeId != assignNodeId) {
                    continue;
                }
                Task task = getTaskFormWorkFlows(x.workflowId, x.taskId);
                double neededResource = task.neededResource;
                double coeff = -1 * neededResource / capacity;
                nodeLoadExpr.addTerm(coeff, x.var);
            }
            loadExprOfEachNode.add(nodeLoadExpr);
        }
        return getSumCost(loadExprOfEachNode, "nodeLoad");
    }


    // 检查并记录每段one-hop link上的链路负载情况
    private GRBQuadExpr prepareExprLink() throws GRBException {
        List<GRBLinExpr> loadExprOfEachLink = new ArrayList<>();
        for (Link link : links) {
            double bandwidth = link.bandwidth;
            GRBLinExpr linkLoadExpr = new GRBLinExpr();
            for (YVar y : yVars) {
                int pathId = y.pathId;
                String pathContent = paths.get(pathId);
                String linkKey = link.getLinkKey();
                if (!isPathContainOneHopLink(pathContent, linkKey)) {
                    continue;
                }
                Flow flow = getFlowFromWorkflows(y.workflowId, y.currTaskId, y.succTaskId);
                double neededBandwidth = flow.neededBandwidth;
                double coeff = -1 * neededBandwidth / bandwidth;
                linkLoadExpr.addTerm(coeff, y.var);
            }
            loadExprOfEachLink.add(linkLoadExpr);
        }
        return getSumCost(loadExprOfEachLink, "linkLoad");
    }

    private GRBQuadExpr getSumCost(List<GRBLinExpr> loadExprOfEachNode, String costPreFix) throws GRBException {
        int nodeNum = 1;
        List<GRBVar> nodeLoadInfo = new ArrayList<>();
        for (GRBLinExpr nodeLoadExpr : loadExprOfEachNode) {
            GRBVar nodeLoad = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, costPreFix + "Var" + nodeNum);
            GRBLinExpr tempConsExpr = new GRBLinExpr();
            tempConsExpr.addTerm(1.0, nodeLoad);
            tempConsExpr.add(nodeLoadExpr);
            model.addConstr(tempConsExpr, GRB.EQUAL, 0.0, costPreFix + "Constr" + nodeNum);
            nodeLoadInfo.add(nodeLoad);
            nodeNum++;
        }
        GRBQuadExpr result = new GRBQuadExpr();
        for (GRBVar var : nodeLoadInfo) {
            result.addTerm(-1, var, var);
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
                if (taskId == task.taskId) {
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
                if (currTaskId == flow.currTask.taskId && succTaskId == flow.succTask.taskId) {
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
        addAssignmentConstraint(groupYVar(yVars));
        // y^{w,p}_{s,s'} == x^w_{s,v} * x^w_{s',v}
        addLinkNodeConstraint();
    }

    private void addLinkNodeConstraint() throws GRBException {
        Map<String, List<YVar>> groupYVarByPath = new HashMap<>();
        for (YVar y : yVars) {
            String pathContent = paths.get(y.pathId);
            String[] pathNodes = pathContent.split(">");
            int uTaskNodeId = Integer.parseInt(pathNodes[0]);
            int vTaskNodeId = Integer.parseInt(pathNodes[pathNodes.length - 1].trim());
            String groupKey = y.workflowId + "_" + y.currTaskId + "_" + y.succTaskId + "_" + uTaskNodeId + "_" + vTaskNodeId;
            List<YVar> groupedYVars = groupYVarByPath.get(groupKey);
            if (null == groupedYVars) {
                groupedYVars = new ArrayList<>();
                groupYVarByPath.put(groupKey, groupedYVars);
            }
            groupedYVars.add(y);
        }
        for (Map.Entry<String, List<YVar>> groupedYVarEntry : groupYVarByPath.entrySet()) {
            String[] keyItems = groupedYVarEntry.getKey().split("_");
            int wfId = Integer.parseInt(keyItems[0]);
            int currTaskId = Integer.parseInt(keyItems[1]);
            int succTaskId = Integer.parseInt(keyItems[2]);
            int uTaskNodeId = Integer.parseInt(keyItems[3]);
            int vTaskNodeId = Integer.parseInt(keyItems[4]);
            GRBQuadExpr sumExpr1 = new GRBQuadExpr();
            for (YVar y : groupedYVarEntry.getValue()){
                sumExpr1.addTerm(1, y.var);
            }
            XVar uXVar = null, vXVar = null;
            for (XVar x : xVars) {
                if (wfId == x.workflowId && currTaskId == x.taskId && uTaskNodeId == x.nodeId) {
                    uXVar = x;
                }
                if (wfId == x.workflowId && succTaskId == x.taskId && vTaskNodeId == x.nodeId) {
                    vXVar = x;
                }
                if (null != uXVar && null != vXVar) {
                    break;
                }
            }
            sumExpr1.addTerm(-1, uXVar.var, vXVar.var);
            model.addQConstr(sumExpr1, GRB.EQUAL, 0, groupedYVarEntry.getKey() + "srcNodeConstr");
        }
    }

    private XVar findXVar(int wfId, int taskId, int nodeId, List<XVar> xVars) {
        for (XVar x : xVars) {
            if (x.workflowId == wfId && x.taskId == taskId && x.nodeId == nodeId) {
                return x;
            }
        }
        return null;
    }

    private void addAssignmentConstraint(Map<String, List<Var>> varMap) throws GRBException {
        Map<String, List<Var>> groupedVar = varMap;
        for (Map.Entry<String, List<Var>> varsEntry : groupedVar.entrySet()) {
            List<Var> vars = varsEntry.getValue();
            GRBLinExpr expr = new GRBLinExpr();
            for (Var var : vars) {
                expr.addTerm(1.0, var.var);
            }
            model.addConstr(expr, GRB.EQUAL, 1.0, varsEntry.getKey());
        }
    }

    // 将变量y^{w,p}_{s,s'}按w_s_s'进行分类
    private Map<String, List<Var>> groupYVar(List<YVar> yVars) {
        Map<String, List<Var>> result = new HashMap<>();
        for (YVar y : yVars) {
            String varNameIndex = y.workflowId + "_" + y.currTaskId + "_" + y.succTaskId;
            List<Var> vars = result.get(varNameIndex);
            if (null == vars) {
                vars = new ArrayList<>();
                result.put(varNameIndex, vars);
            }
            vars.add(y);
        }
        return result;
    }

    // 将变量x^w_{s,v}按w_s进行分类
    private Map<String, List<Var>> groupXVar(List<XVar> xVars) {
        Map<String, List<Var>> result = new HashMap<>();
        for (XVar x : xVars) {
            String varNameIndex = x.workflowId + "_" + x.taskId;
            List<Var> vars = result.get(varNameIndex);
            if (null == vars) {
                vars = new ArrayList<>();
                result.put(varNameIndex, vars);
            }
            vars.add(x);
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
                for (Map.Entry<Integer, Double> nodeEntry : nodes.entrySet()) {
                    XVar xVar = new XVar(wfId, task.taskId, nodeEntry.getKey(), null);
                    String varName = xVar.getVarName();
                    GRBVar v = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    xVar.var = v;
                    xVars.add(xVar);
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
                int currTaskId = flow.currTask.taskId;
                int succTaskId = flow.succTask.taskId;
                for (Map.Entry<Integer, String> pathEntry : paths.entrySet()) {
                    int pathId = pathEntry.getKey();
                    YVar y = new YVar(wfId, pathId, currTaskId, succTaskId, null);
                    String varName = y.getVarName();
                    GRBVar yVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    y.var = yVar;
                    yVars.add(y);
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
            int u = Integer.parseInt(items[1]);
            int v = Integer.parseInt(items[3]);
            double capacity = Double.valueOf(items[5]);
            Link link = new Link(u, v, capacity);
            links.add(link);
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
        int originWFNum = 6;
        WorkflowGenerator workflowGenerator = WorkflowGenerator.getWorkflowGenerator();
        for(int i = 0; i < originWFNum; i++){
            workflows.add(workflowGenerator.generateAWorkflow_V2(workflowTemplateIdx));
        }
    }
}