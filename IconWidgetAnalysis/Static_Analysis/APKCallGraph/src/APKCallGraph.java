
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;
import org.jgrapht.ext.ComponentNameProvider;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.graph.DirectedPseudograph;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import org.xmlpull.v1.XmlPullParserException;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.options.Options;
import soot.tagkit.AnnotationEnumElem;
import soot.tagkit.LineNumberTag;
import soot.tagkit.SourceLineNumberTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;
import soot.util.queue.QueueReader;

import javax.sound.sampled.Line;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane;

public class APKCallGraph {


	static DirectedPseudograph<APKCallGraph.MethodNode, CallEdge> jg = new DirectedPseudograph<>(CallEdge.class);
	static HashMap<String, APKCallGraph.MethodNode> methods = new HashMap<>();
	static HashMap<SootMethod, Boolean> visited = new HashMap<SootMethod, Boolean>();
	static ArrayList<SootMethod> methodsList = new ArrayList<>();
	static APKCallGraph apkg = new APKCallGraph();

	static ArrayList<SootMethod> handleMessageMethods = new ArrayList<>();
	static ArrayList<SootMethod> asyncExecuteMethods = new ArrayList<>();
	static ArrayList<SootMethod> clickMethods = new ArrayList<>();
	static ArrayList<SootMethod> threadTgt = new ArrayList<>();

	static ArrayList<String> threadSrc = new ArrayList<>();

	static int edgeId = 0;
	static int nodeId = 0;
	static boolean isGenerated = false;


	static IC3ProtobufParser ic3parser = new IC3ProtobufParser();



	static HashMap<String, List<String>> edges = new HashMap<>();
	static HashMap<String, List<String>> afterICC = new HashMap<>();

	//static Connection connection = null;
	static HashMap<String, ArrayList<String>> lineVSHdl = new HashMap<>();
	static HashMap<String, ArrayList<String>> HdlVSPM = new HashMap<>();
	static HashMap<String, ArrayList<String>> permMethods = new HashMap<>();
	static ArrayList<String> handlers = new ArrayList<>();
	static HashMap<String, ArrayList<Stmt>> methodToStmts = new HashMap<>();

	static String androidPlatformPath = "/Users/shaoyang/Library/Android/sdk/platforms/android-18/android.jar";





	class MethodNode {
		SootMethod m;
		String signature;
		public int id;

		public MethodNode(SootMethod m, int id) {
			this.m = m;

			if (m != null) {
				signature = m.getSignature();
			}

			this.id = id;
		}

		public MethodNode(String name, int id){
			this.signature = name;
			this.id = id;
		}

		public String getSignature() {
			return signature;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((signature == null) ? 0 : signature.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MethodNode other = (MethodNode) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (signature == null) {
				if (other.signature != null)
					return false;
			} else if (!signature.equals(other.signature))
				return false;
			return true;
		}

		private APKCallGraph getOuterType() {
			return APKCallGraph.this;
		}

	}

	class CallEdge {
		private MethodNode source;
		private MethodNode target;
		private int id;

		public MethodNode getSource() {
			return source;
		}

		public void setSource(MethodNode source) {
			this.source = source;
		}

		public MethodNode getTarget() {
			return target;
		}

		public void setTarget(MethodNode target) {
			this.target = target;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public CallEdge(MethodNode source, MethodNode target, int id) {
			super();
			this.source = source;
			this.target = target;
			this.id = id;
		}

	}

	class MethodNodeNameProvider implements ComponentNameProvider<MethodNode> {

		@Override
		public String getName(MethodNode e) {
			return e.getSignature();
		}

	}

	class CallEdgeLabelProvider implements ComponentNameProvider<CallEdge> {

		@Override
		public String getName(CallEdge e) {
			return e.toString();
		}

	}

	class MethodnodeIdProvider implements ComponentNameProvider<MethodNode> {
		@Override
		public String getName(MethodNode e) {
			return "" + e.id;
		}

	}


	public static void main(String[] args) throws Exception {
		/*
		 * Main function, basic running configs, change dir(s) before running.
		 * Scan every input widget-handler mapping, skip when no mapping is found.
		 * If mapping is found, build extended static call graph of the app, extract subgraph(s), check API(s) and permission.
		 */
		String apk = "com.Abby_Alex";
		//String apk = args[0].substring(args[0].lastIndexOf("/"), args[0].indexOf(".apk"));
		//String appPath = args[1];
		String appPath = "/Users/shaoyang/Desktop/";
		//String inputCSVPath = args[2];
		String inputCSVPath = "/Users/shaoyang/Desktop/";
		//String permissionOutput = args[3];
		String permissionOutput = "/Users/shaoyang/Desktop/";
		//String apk = "nextcloud";
		//String ic3 = args[4];
		String ic3 = "/Users/shaoyang/Desktop/ic3output/";

		System.out.println("Start analyze apk: " + apk + ".apk");

		String apkPath = appPath + apk + ".apk";
		int numberOfLines = 0;
		boolean hasHandler = false;
		String inputCSV = apk + "_img2widgets.csv";
		File file = new File(inputCSVPath + inputCSV);
		FileInputStream fis = new FileInputStream(file);
		Scanner scanner = new Scanner(fis);
		while(scanner.hasNextLine()){
			String line = scanner.nextLine();
			int front = line.indexOf("[");
			int back = line.indexOf("]");
			if ((back - front) > 1){
				hasHandler = true;
				numberOfLines ++;
				break;
			}else {
				hasHandler = false;
			}
			numberOfLines ++;
		}

		if (numberOfLines == 1){
			System.out.println("No widget found.");
		}else if (numberOfLines > 1 && hasHandler != true) {
			System.out.println("No handler found.");
		}else {
			generateCallGraph(apk, apkPath, ic3);
			getHandlers(apk,inputCSVPath + inputCSV);

			if (permMethods.size() > 0){
				writeInfoToFile(permissionOutput, apk);
			}
		}
	}

	public static void generateCallGraph(String apk, String apkPath, String ic3) throws IOException, XmlPullParserException {
		/*
		 * Soot configs
		 * Running BFS to build call graph
		 * Integrate multi-threading methods, callbacks, ICC and so on.
		 */
		SetupApplication app = new SetupApplication(androidPlatformPath, apkPath);
		Options.v().set_android_api_version(18);
		app.calculateSourcesSinksEntrypoints("/Users/shaoyang/Work/APKCallGraph/SourcesAndSinks.txt");
		soot.G.reset();
		Options.v().set_keep_line_number(true);
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_process_dir(Collections.singletonList(apkPath));
		Options.v().set_force_android_jar(androidPlatformPath);
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("cg.cha", "enabled:true");
		Options.v().setPhaseOption("cg", "all-reachable:true");
		PhaseOptions.v().setPhaseOption("tag.ln", "on");
		Options.v().set_android_api_version(18);
		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_keep_line_number(true);
		Options.v().set_output_format(Options.output_format_jimple);
		app.setCallbackFile("/Users/shaoyang/Work/APKCallGraph/AndroidCallbacks.txt");

		Scene.v().loadNecessaryClasses();

		SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
		Options.v().set_main_class(entryPoint.getSignature());
		Scene.v().setEntryPoints(Collections.singletonList(entryPoint));

		PackManager.v().runPacks();
		Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
		for (SootClass sootClass : applicationClasses) {

			List<SootMethod> ms = sootClass.getMethods();
			for (SootMethod m : ms) {
				if (methodsList.contains(m)) {
					continue;
				}
				methodsList.add(m);
			}
		}

		CopyOnWriteArrayList<SootMethod> list = new CopyOnWriteArrayList<>(methodsList);

		while (list.size() > 0){
			if (visited.containsKey(list.get(0)) == false){
				if (!list.get(0).hasActiveBody()){
					list.remove(list.get(0));
					continue;
				}
				Body body = list.get(0).retrieveActiveBody();
				Iterator<Unit> stmts = body.getUnits().iterator();
				visited.put(list.get(0), true);
				while (stmts.hasNext()) {
					Stmt s = (Stmt) stmts.next();
					if (s.toString().contains("invoke")) {

						if (list.get(0).getSignature().contains("void handleMessage(android.os.Message)>")){
							if (!handleMessageMethods.contains(list.get(0))){
								handleMessageMethods.add(list.get(0));
							}
						}

						if ((list.get(0).getSignature().contains("doInBackground(")) ||
								(list.get(0).getSignature().contains("onPreExecute(")) ||
								(list.get(0).getSignature().contains("onPostExecute("))){
							if (!asyncExecuteMethods.contains(list.get(0))){
								asyncExecuteMethods.add(list.get(0));
							}
						}
						if (list.get(0).getSignature().contains(": void onClick(")){
							if (!clickMethods.contains(list.get(0))){
								clickMethods.add(list.get(0));
							}
						}
						if (list.get(0).getSignature().contains(": void run()")){
							if (list.get(0).getDeclaringClass().getSuperclass().toString().contains("java.lang.Thread")){
								if (!threadTgt.contains(list.get(0))){
									threadTgt.add(list.get(0));
									if (!threadSrc.contains(list.get(0).getDeclaringClass().toString() + ": void start()>")){
										threadSrc.add("<" + list.get(0).getDeclaringClass().toString() + ": void start()>");
									}
								}
							}
						}

						try{
							InvokeExpr expr = s.getInvokeExpr();
							if (!edges.containsKey(list.get(0).getSignature())){
								List<String> temp = new ArrayList<>();
								if (expr.getMethod().getDeclaringClass().toString() == "java.lang.Thread"){
									temp.add(expr.getMethodRef().getSignature());
								}else {
									temp.add(expr.getMethod().getSignature());
								}

								edges.put(list.get(0).getSignature(), temp);
							}else if (!edges.get(list.get(0).getSignature()).contains(expr.getMethodRef().getSignature())){
								edges.get(list.get(0).getSignature()).add(expr.getMethodRef().getSignature());
							}
							methodsList.add(expr.getMethod());
							list.add(expr.getMethod());
							if (methodToStmts.get(expr.getMethod()) != null){
								methodToStmts.get(expr.getMethod()).add(s);
							}
							else{
								ArrayList<Stmt> temp = new ArrayList<>();
								temp.add(s);
								methodToStmts.put(expr.getMethod().getSignature(), temp);
							}
						}catch (Exception e){
							System.out.println("getInvokeExpr() called with no invokeExpr present!");
						}
					}
				}

			}
			list.remove(list.get(0));
		}


		for (String src: edges.keySet()){
			for (String tgt: edges.get(src)){
				edgeId++;

				if (!methods.containsKey(src)) {
					nodeId++;
					methods.put(src, apkg.new MethodNode(src, nodeId));
					jg.addVertex(methods.get(src));
				}

				if (!methods.containsKey(tgt)) {
					nodeId++;
					methods.put(tgt, apkg.new MethodNode(tgt, nodeId));
					jg.addVertex(methods.get(tgt));
				}
				MethodNode srcNode = methods.get(src);
				MethodNode tgtNode = methods.get(tgt);

				jg.addEdge(srcNode, tgtNode, apkg.new CallEdge(srcNode, tgtNode, edgeId));
			}
		}


		boolean useic3 = true;
		if (useic3) {
			File ic3folder = new File(ic3 + "/" + apk);
			File[] listFiles = ic3folder.listFiles();
			if (listFiles == null) {
				System.out.println(ic3 + "/" + apk + " is null. ");
			}
			if (listFiles != null && listFiles.length > 0) {
				for (File listfile : listFiles) {
					HashMap<String, HashSet<String>> m2providers = new HashMap<>();
					HashMap<String, HashSet<String>> m2intents = new HashMap<>();
					HashMap<String, ArrayList<String>> iccs = ic3parser.parseFromFile(listfile.getAbsolutePath(),
							m2providers, m2intents);

					// for service class, you should add an edge from a method

					for (Entry<String, ArrayList<String>> icc : iccs.entrySet()) {
						String fromMethod = icc.getKey();
						ArrayList<String> toClasses = icc.getValue();

						if (!methods.containsKey(fromMethod)) {
							System.out.println("error finding from method: " + fromMethod);
							continue;
						}

						MethodNode from = methods.get(fromMethod);
						for (String clazz : toClasses) {
							if (clazz.length() == 0) {
								// System.out.println("empty clazz");
								continue;
							}
							SootClass loadClass = Scene.v().loadClassAndSupport(clazz);

							List<SootMethod> loadMethods = loadClass.getMethods();
							for (SootMethod loadMethod : loadMethods) {
								if (loadMethod.getName().startsWith("onCreate")
										|| loadMethod.getName().startsWith("onStart")) {
									if (!methods.containsKey(loadMethod.getSignature())) {
										nodeId++;
										methods.put(loadMethod.getSignature(), apkg.new MethodNode(loadMethod, nodeId));
										jg.addVertex(methods.get(loadMethod.getSignature()));
									}

									//edgeId++;
									MethodNode to = methods.get(loadMethod.getSignature());
									//jg.addEdge(from, to, apkg.new CallEdge(from, to, edgeId));
								}
							}

						}

						for (String src: edges.get(fromMethod)){
							System.out.println(src);
							if (!src.contains(": void startActivity(")){
								continue;
							}else{
								String tgt = "<" + iccs.get(fromMethod).get(0) + ": void onCreate(android.os.Bundle)>";
								if (jg.containsVertex(methods.get(tgt)) == true){
									if (afterICC.containsKey(fromMethod) == false) {
										List<String> temp = new ArrayList<>();
										temp.add(src);
										temp.add(tgt);
										afterICC.put(fromMethod, temp);
									}
									if (edges.containsKey(src)){
										edges.get(src).add(tgt);
									}else{
										List<String> temp = new ArrayList<>();
										temp.add(tgt);
										edges.put(src, temp);
									}
									jg.addEdge(methods.get(src), methods.get(tgt), apkg.new CallEdge(methods.get(src), methods.get(tgt), edgeId++));
								}else{
									continue;
								}
							}
						}
					}
				}

			}
		}

		for (String m: methods.keySet()){
			connectThread(m);
			connectSendMessage(m);
			connectAsyncExecute(m);
			connectClickCall(m);
		}


		DOTExporter<MethodNode, CallEdge> exporter = new DOTExporter<MethodNode, CallEdge>(
				apkg.new MethodnodeIdProvider(), apkg.new MethodNodeNameProvider(), null);
		File file = new File("/Users/shaoyang/Downloads/Static_Analysis/dot_output/" + apk + "/");
		file.mkdir();
		exporter.exportGraph(jg, new FileWriter("/Users/shaoyang/Downloads/Static_Analysis/dot_output/" + apk + "/" + apk + ".dot"));
	}



	 public static void connectThread(String src){
		if (threadSrc.contains(src)){
			MethodNode srcNode = methods.get(src);
			int i = src.indexOf(":");
			for (SootMethod tgt: threadTgt){
				if (tgt.getSignature().contains(src.substring(0,i))){
					MethodNode tgtNode = methods.get(tgt.getSignature());
					if (edges.containsKey(src)){
						edges.get(src).add(tgt.getSignature());
					}else{
						List<String> temp = new ArrayList<>();
						temp.add(tgt.getSignature());
						edges.put(src, temp);
					}
					jg.addEdge(srcNode, tgtNode, apkg.new CallEdge(srcNode, tgtNode, edgeId++));
				}
			}
			System.out.println("Connect thread successful");
		}
	 }

	 public static void connectClickCall(String  src){
		if (src.contains(": void setOnClickListener(")){
			for (SootMethod tgt: clickMethods){
				MethodNode srcNode = methods.get(src);
				MethodNode tgtNode = methods.get(tgt.getSignature());
				if (edges.containsKey(src)){
					edges.get(src).add(tgt.getSignature());
				}else{
					List<String> temp = new ArrayList<>();
					temp.add(tgt.getSignature());
					edges.put(src, temp);
				}
				jg.addEdge(srcNode, tgtNode, apkg.new CallEdge(srcNode, tgtNode, edgeId++));
			}
			System.out.println("Connect click calls successful");
		}
	 }

	 public static void connectSendMessage(String src){
		if (src.contains("boolean sendMessage(android.os.Message)>")){
			for (SootMethod tgt: handleMessageMethods){
				MethodNode srcNode = methods.get(src);
				MethodNode tgtNode = methods.get(tgt.getSignature());
				if (edges.containsKey(src)){
					edges.get(src).add(tgt.getSignature());
				}else{
					List<String> temp = new ArrayList<>();
					temp.add(tgt.getSignature());
					edges.put(src, temp);
				}
				jg.addEdge(srcNode, tgtNode, apkg.new CallEdge(srcNode, tgtNode, edgeId++));
			}
			System.out.println("Connect message calls successful");
		}
	 }

	 public static void connectAsyncExecute(String src){
		if ((src.contains("android.os.AsyncTask execute(java.lang.Object[])>")) || src.contains("AsyncTask executeOnExecutor(")){
			for (SootMethod tgt: asyncExecuteMethods){
				MethodNode srcNode = methods.get(src);
				MethodNode tgtNode = methods.get(tgt.getSignature());
				if (edges.containsKey(src)){
					edges.get(src).add(tgt.getSignature());
				}else{
					List<String> temp = new ArrayList<>();
					temp.add(tgt.getSignature());
					edges.put(src, temp);
				}
				jg.addEdge(srcNode, tgtNode, apkg.new CallEdge(srcNode, tgtNode, edgeId++));
			}
			System.out.println("Connect asynctask calls successful");
		}
	 }

	 public static void generateSubGraphOfMethod(String apk, String method) throws IOException, SQLException {
		/*
		 * This function is used to generate subgraph of an event handler.
		 * Each node in the subgraph will be checked if it is related to certain permissions.
		 */
		DirectedPseudograph<APKCallGraph.MethodNode, CallEdge> subGraph = new DirectedPseudograph<>(CallEdge.class);

		ArrayList<String> subgraph = new ArrayList<>();
		if(!methods.containsKey(method)){
			System.out.println("Method not found, please enter again!");
		}else {
			HashMap<String, Boolean> hasVisited = new HashMap<>();
			List<String> list = new ArrayList<>();
			list.add(method);
			hasVisited.put(method, true);
			while (list.size() > 0) {
				if (edges.get(list.get(0)) == null){
					list.remove(list.get(0));
					continue;
				}

				for (String tgt: edges.get(list.get(0))){
					if (list.get(0).contains(": void startActivity(")){
						if (afterICC.get(method) == null){
							continue;
						}
					}
					//System.out.println(tgt);
					if (!subGraph.containsVertex(methods.get(list.get(0)))){
						subGraph.addVertex(methods.get(list.get(0)));
					}
					if (!subGraph.containsVertex(methods.get(tgt))){
						subGraph.addVertex(methods.get(tgt));
					}
					subGraph.addEdge(methods.get(list.get(0)), methods.get(tgt), apkg.new CallEdge(methods.get(list.get(0)), methods.get(tgt), edgeId++));

					if (hasVisited.containsKey(tgt)){
						continue;
					}
					hasVisited.put(tgt, true);
					list.add(tgt);
					subgraph.add(tgt);
				}
				list.remove(list.get(0));
			}


			DOTExporter<MethodNode, CallEdge> exporter = new DOTExporter<MethodNode, CallEdge>(
					apkg.new MethodnodeIdProvider(), apkg.new MethodNodeNameProvider(), null);
			exporter.exportGraph(subGraph, new FileWriter("/Users/shaoyang/Downloads/Static_Analysis/dot_output/" + apk + "/" + method + ".dot"));

			isGenerated = true;

		}
		System.out.println("Handler---------------" + method);
		System.out.println(".....................................................");
		for (String m: subgraph){
			System.out.println("Checking permissions");
			System.out.println("Method----------------" + m);
			String permission = getPermission(m.replace("\'", ""));
			if (permission == null){
				continue;
			}
			else {
				if (HdlVSPM.get(method) == null){
					ArrayList<String> temp = new ArrayList<>();
					temp.add(permission);
					HdlVSPM.put(method, temp);
				}
				else{
					HdlVSPM.get(method).add(permission);
				}
			}
			if (permMethods.get(m) == null){
				ArrayList<String> permissions = new ArrayList<>();
				permissions.add(permission);
				permMethods.put(m, permissions);
			}
		}
	 }

	public static String getPermission(String method) throws SQLException{
		/*
		 * Connect mysql PScout mapping database.
		 * Run sql given certain APIs, and return corresponding permissions.
		 */
		Connection connection = null;

		String permission = null;
		String methodClass = method.substring(1, method.indexOf(":"));
		String sql = "select Permission from outputmapping where Method = '" + method + "'";
		String driver = "com.mysql.cj.jdbc.Driver";
		String url = "jdbc:mysql://localhost:3306/APKCalls?user=root&password=jiaozhuys05311&serverTimezone=GMT";
		try {
			Class.forName(driver);
			connection = DriverManager.getConnection(url);
			Statement stmt = connection.createStatement();
			ResultSet resultSet = stmt.executeQuery(sql);
			if (resultSet.next() == true){
				permission = resultSet.getString(1);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		connection.close();
		return permission;
	}


	public static void getHandlers(String apk, String fileName) throws IOException, SQLException {
		/*
		 * Using regex to match event handlers.
		 * If handler is found, it is used to generate subgraph.
		 */
		System.out.println("Processing file :" + fileName);
		try {
			String regex = "<(.*?)>";
			FileReader f_reader = new FileReader(fileName);
			BufferedReader br = new BufferedReader(f_reader);
			String line = "";
			while ((line = br.readLine()) != null){
				String tempLine = "";
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(line);
				while (matcher.find()){
					tempLine = line.substring(0, line.indexOf("["));
					if (handlers.contains(matcher.group(1))){
						if (!lineVSHdl.containsKey(tempLine)){
							ArrayList<String> temp = new ArrayList<>();
							temp.add("<" + matcher.group(1) + ">");
							lineVSHdl.put(tempLine, temp);
							continue;
						}else{
							lineVSHdl.get(tempLine).add("<" + matcher.group(1) + ">");
							continue;
						}
					}
					handlers.add(matcher.group(1));
					//System.out.println(line);
					if (lineVSHdl.containsKey(tempLine) == false){
						ArrayList<String> temp = new ArrayList<>();
						temp.add("<" + matcher.group(1) + ">");
						lineVSHdl.put(tempLine, temp);
						generateSubGraphOfMethod( apk, "<" + matcher.group(1) + ">");
					}else {
						lineVSHdl.get(tempLine).add("<" + matcher.group(1) + ">");
						generateSubGraphOfMethod( apk, "<" + matcher.group(1) + ">");
					}
				}
			}
		}catch (Exception e){
			System.out.println(e.toString());
		}
	}

	public static void writeInfoToFile(String permissionOutput, String apkName) throws IOException {
		File file = new File(permissionOutput + apkName + "_permission.csv");
		if (file.exists() == false){
			file.createNewFile();
		}
		FileWriter f_writer = new FileWriter(file);
		BufferedWriter bw = new BufferedWriter(f_writer);
		bw.write("APK\tImage\tWID\tWID Name\tLayout\tHandler\tMethod\tLines\tPermissions\n");
		for (String line: lineVSHdl.keySet()){
			for (String handler: HdlVSPM.keySet()){
				if (!lineVSHdl.get(line).contains(handler)){
					continue;
				}
				for (String method: permMethods.keySet()){
					for (SootMethod m: methodsList){
						if (m.toString() == method){
							ArrayList<Stmt> stmts = methodToStmts.get(m.getSignature());
							ArrayList<String> lineNums = new ArrayList<>();
							for (Stmt s: stmts){
								Unit u = (Unit)s;
								LineNumberTag tag = (LineNumberTag)u.getTag("LineNumberTag");
								lineNums.add(String.valueOf(tag.getLineNumber()));
							}
							bw.write(line  + handler + "\t" +
									method + "\t" +
									lineNums + "\t" +
									permMethods.get(method) + "\n");

						}
						else{
							continue;
						}
					}
				}
			}
		}
		bw.close();
	}
}
