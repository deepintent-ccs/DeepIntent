
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

import org.jgrapht.ext.ComponentNameProvider;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.graph.DirectedPseudograph;

import soot.*;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.tagkit.SourceLineNumberTag;
import soot.toolkits.graph.*;
import soot.toolkits.graph.pdg.MHGDominatorTree;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class APKCFG {
	
	public static void main(String[] args) throws Exception{
		APKCFG apkg = new APKCFG();

		//String androidPlatformPath = "/Users/xushengxiao/Android/platforms/android-18/android.jar"; // do
																									// not
																									// use
																									// ~
		String appPath = "apks";
		String apk = "DroidDream";
		String apkPath = appPath + "/" + apk + ".apk";
		apkg.generateCFG(apkPath);
	}

    public void generateCFG(String appPath) throws Exception {
//        String androidPlatformPath = "D:/Android/sdk/platforms/android-23/android.jar";
//        String androidPlatformPath = "/Users/majunqi0102/Library/Android/sdk/platforms/android-23/android.jar";
    	String androidPlatformPath = "/Users/shaoyang/Library/Android/sdk/platforms/android-18/android.jar";
        SetupApplication app = new SetupApplication(androidPlatformPath, appPath);
        // app.
        Options.v().set_android_api_version(22);
        app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
        soot.G.reset();

        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(appPath));
        // Options.v().set_android_jars(androidPlatformPath);
        Options.v().set_force_android_jar(androidPlatformPath);
        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().set_android_api_version(22);
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_output_format(Options.output_format_jimple);
        app.setCallbackFile("AndroidCallbacks.txt");

        Scene.v().loadNecessaryClasses();

        SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
        Options.v().set_main_class(entryPoint.getSignature());
        Scene.v().setEntryPoints(Collections.singletonList(entryPoint));
        System.out.println(entryPoint.getActiveBody());

        PackManager.v().runPacks();


        printAugmentedCFG();

    }

    class CDNode {
        // assume Y is control dependent on X.
        private Unit nodeY;
        private int sourceLineNumberY;
        private Unit nodeX;
        private int sourceLineNumberX;

        int id;

        public CDNode(Unit nodeY, int sourceLineNumberY, Unit nodeX, int sourceLineNumberX, int id) {
            this.nodeY = nodeY;
            this.sourceLineNumberY = sourceLineNumberY;
            this.nodeX = nodeX;
            this.sourceLineNumberX = sourceLineNumberX;
            this.id = id;
        }
    }

    class CDEdge {
        private CDNode srcNode;
        private CDNode tgtNode;
        private int id;

        public CDEdge(CDNode srcNode, CDNode tgtNode, int id) {
            this.srcNode = srcNode;
            this.tgtNode = tgtNode;
            this.id = id;
        }
    }

	private void printAugmentedCFG() throws IOException {
        Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
        for (SootClass sootClass : applicationClasses) {
            List<SootMethod> methods = sootClass.getMethods();
            for (SootMethod method : methods) {
                if (!sootClass.getName().contains("MainActivity") || !method.getName().equals("generateRandom")) {
                    //|| !method.getName().equals("onCreate")
                    continue;
                }

                Body body = method.retrieveActiveBody();
                ExceptionalUnitGraph cfg = new ExceptionalUnitGraph(body);
                ExceptionalUnitGraph duplicateCfg = new ExceptionalUnitGraph(body);

//                CFGToDotGraph cfgToDotGraph = new CFGToDotGraph();
//                DotGraph dotGraph = cfgToDotGraph.drawCFG(cfg, body);
//                dotGraph.plot("cfgs/" + method.getName() + ".out"); // print cfg

                List<Unit> entryNodeList = cfg.getHeads();
                if (entryNodeList.size() <= 0) {
                    System.out.println("*********************");
                    System.out.println("no CFG");
                    System.out.println("*********************");
                    return;
                }
                Unit entryNode = entryNodeList.get(0);

                MHGPostDominatorsFinder<Unit> pdomFinder = new MHGPostDominatorsFinder(cfg);
                MHGDominatorTree<Unit> pdomTree = new MHGDominatorTree(pdomFinder);
                CytronDominanceFrontier<Unit> cdf = new CytronDominanceFrontier(pdomTree);

                int srcLineNo, srcLineNoOld = -1;

                //this map is a must to store the SourceLineNumber of a node, because the node from DominatorTree will lost the SourceLineNumber.
                Map<Unit, Integer> nodeSourceLineNumberMap = new HashMap<>();
                nodeSourceLineNumberMapping(nodeSourceLineNumberMap, duplicateCfg, entryNodeList);

                Map<Unit, CDNode> cdNodeMap = new HashMap<>();
                DirectedPseudograph<CDNode, CDEdge> dg = new DirectedPseudograph<>(CDEdge.class);

                int nodeId = 0;
                int edgeId = 0;

                int entryNodeIndex = 1;
                int entryNodeSourceLineNumber = -1;

                for (Iterator<Unit> nodesIt = cfg.iterator(); nodesIt.hasNext(); ) {
                    Unit nodeY = nodesIt.next();
                    nodeId++;

                    if (entryNodeList.size() > 0 && entryNodeList.size() > entryNodeIndex && (entryNodeList.get(entryNodeIndex)+"").equals(nodeY+"")) {
                        entryNode = entryNodeList.get(entryNodeIndex);
                        srcLineNo = entryNode.getJavaSourceStartLineNumber();
                        srcLineNoOld = srcLineNo;
                        entryNodeSourceLineNumber = srcLineNo;
                        entryNodeIndex++;
                    }

                    srcLineNo = nodeY.getJavaSourceStartLineNumber();
                    if ((nodeY+"").equals("if $i0 <= $i1 goto $r0 = new java.util.Random")) {
                        int i = 0;
                    }

                    if (srcLineNo != entryNodeSourceLineNumber) {
                        //if two nodes are in the same line in source code, it won't have SourceLineNumber as attribute.
                        srcLineNoOld = srcLineNo;
                    }

                    //get the control dependent of a node.
                    List<DominatorNode<Unit>> domNodeList = cdf.getDominanceFrontierOf(pdomTree.getDode(nodeY));
                    Unit nodeX = null;
                    if (domNodeList.size() == 0) {
                        //entry node can be more than one if this method throws exceptions.
                        nodeX = entryNode;
                    } else if (domNodeList.size() == 1) {
                        //this line of code is under a branch
                        System.out.println("********* this is a branch ********");
                        nodeX = pdomTree.getDode(domNodeList.get(0).getGode()).getGode();

                        //if this line of code is also in a condition statement. eg if(int a == b), a here is nodeY, a's control dependence is itself in CDG, but the entry point
                        if (nodeSourceLineNumberMap.get(nodeX) == srcLineNoOld) {
                            nodeX = entryNode;
                        }
                    } else if (domNodeList.size() >= 2) {
                        //this line of code depends on a branch of a multiple conditions statement. eg: if(a || b)
                        Unit tmpNode = domNodeList.get(0).getGode();
                        for (int i = 1; i < domNodeList.size(); i++) {
                            if (nodeSourceLineNumberMap.get(tmpNode) != (nodeSourceLineNumberMap.get(domNodeList.get(i).getGode()))) {
                                // if one statement has more than 2 control dependence nodes.
                                // it's a situation that I can't understand clearly.
                            }
                        }
                        nodeX = domNodeList.get(0).getGode();
                    }

                    CDNode node = new CDNode(nodeY, srcLineNoOld, nodeX, nodeSourceLineNumberMap.get(nodeX), nodeId);
                    cdNodeMap.put(nodeY, node);
                    dg.addVertex(node);
                    System.out.println(nodeY + " --> Line " + srcLineNoOld + " || control dependent on " + nodeX + " --> Line " + nodeSourceLineNumberMap.get(nodeX));
                    System.out.println();
                }

                //Here is the code to add edges
                Queue<Unit> queue = new LinkedList<>(entryNodeList);
                //this set is used for eliminating loops if there is loops in cfg.
                //I don't if soot.graph has function like "outgoingEdgesOf" JGraphT provided.
                Set<Unit> addedNodeSet = new HashSet<>();

                while (!queue.isEmpty()) {
                    Unit srcNode = queue.poll();
                    List<Unit> successorNodeList = cfg.getSuccsOf(srcNode);
                    for (Iterator<Unit> successorNodeIt = successorNodeList.iterator(); successorNodeIt.hasNext(); ) {
                        edgeId++;
                        Unit succNode = successorNodeIt.next();
                        if (addedNodeSet.contains(succNode)) {
                            continue;
                        }
                        addedNodeSet.add(succNode);
                        queue.offer(succNode);
                        dg.addEdge(cdNodeMap.get(srcNode), cdNodeMap.get(succNode), new CDEdge(cdNodeMap.get(srcNode), cdNodeMap.get(succNode), edgeId));
                    }
                }


                DOTExporter<CDNode, CDEdge> exporter = new DOTExporter<>(new CDNodeIdProvider(), new CDNodeNameProvider(), null);
                Path path = new File("cfgs/" + sootClass.getName() + "/").toPath();
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                exporter.exportGraph(dg, new FileWriter(path + "/" + method.getName() + ".out"));
            }

        }
        System.out.println();
    }

    private void nodeSourceLineNumberMapping(Map<Unit, Integer> nodeSourceLineNumberMap, ExceptionalUnitGraph cfg, List<Unit> entryNodeList) {
        int srcLineNo, srcLineNoOld = -1;

        for (Iterator<Unit> nodesIt = cfg.iterator(); nodesIt.hasNext();) {
            Unit node = nodesIt.next();

            srcLineNo = node.getJavaSourceStartLineNumber();
            if (srcLineNo != -1) {
                //if two nodes are in the same line in source code, it won't have SourceLineNumber as attribute.
                srcLineNoOld = srcLineNo;
            }
            nodeSourceLineNumberMap.put(node, srcLineNoOld);
        }
    }

  

    class CDNodeNameProvider implements ComponentNameProvider<CDNode> {

        @Override
        public String getName(CDNode cdNode) {
            String s = cdNode.nodeY + " --> Line " + cdNode.sourceLineNumberY + " || control dependent on " + cdNode.nodeX + " --> Line " + cdNode.sourceLineNumberX;
            // when converting dot to svg, double quotes will fail the converting, so get rid of it.
            if (s.contains("\"")) {
                s = s.replace('\"', '\'');
            }
            return s;
        }
    }

    class CDEdgeLabelProvider implements ComponentNameProvider<CDEdge> {

        public String getName(CDEdge cdEdge) {
            return cdEdge.toString();
        }
    }

    class CDNodeIdProvider implements ComponentNameProvider<CDNode> {

        @Override
        public String getName(CDNode cdNode) {
            return cdNode.id + "";
        }
    }
}
