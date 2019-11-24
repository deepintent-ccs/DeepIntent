package presto.android.gui.clients;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdk.nashorn.internal.runtime.JSONFunctions;
import presto.android.Configs;
import presto.android.Debug;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.clients.energy.EnergyAnalyzer;
import presto.android.gui.clients.energy.EnergyUtils;
import presto.android.gui.clients.energy.Pair;
import presto.android.gui.clients.energy.VarUtil;
import presto.android.gui.graph.*;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.WTGBuilder;
import presto.android.gui.wtg.ds.HandlerBean;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import soot.SootMethod;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Created by zero on 10/21/15.
 */
public class WTGDemoClient implements GUIAnalysisClient {
	@Override
	public void run(GUIAnalysisOutput output) {
		VarUtil.v().guiOutput = output;
		Configs.debugCodes.add(Debug.DUMP_CCFX_DEBUG);
		String[] split = Configs.benchmarkName.split("/");
		String apkname = split[split.length - 1];
		WTGBuilder wtgBuilder = new WTGBuilder(apkname);
		wtgBuilder.build(output);
		WTGAnalysisOutput wtgAO = new WTGAnalysisOutput(output, wtgBuilder);
		WTG wtg = wtgAO.getWTG();

		Collection<WTGEdge> edges = wtg.getEdges();
		Collection<WTGNode> nodes = wtg.getNodes();

		Multimap<NObjectNode, NObjectNode> guiHierarchy = wtgBuilder.guiHierarchy;
		Multimap<NObjectNode, HandlerBean> widgetToHandlers = wtgBuilder.widgetToHandlers;
		Multimap<NObjectNode, NIdNode> widgetToImages = wtgBuilder.widgetToImages;
		Map<NObjectNode, Set<NIdNode>> viewToLayoutIds = output.getSolver().getViewToLayoutIds();
		
		Logger.verb("DEMO", "Application: " + Configs.benchmarkName);
		Logger.verb("DEMO", "Launcher Node: " + wtg.getLauncherNode());
		Logger.verb("DEMO", "========================");
		Logger.verb("DEMO", "viewToLayoutIds: " + viewToLayoutIds);
		Logger.verb("DEMO", "========================");
		PrintWriter out = null;
		try {
			out = new PrintWriter("output/" + apkname + ".json");
			JSONArray wins = new JSONArray();
			for (WTGNode n : nodes) {
				JSONObject win = new JSONObject();
				wins.add(win);
				if (viewToLayoutIds.containsKey(n.getWindow())) {
					win.put("name", n.getWindow().toString() + viewToLayoutIds.get(n.getWindow()));
				} else {
					win.put("name", n.getWindow().toString());
				}
				JSONArray jsonviews = new JSONArray();
				win.put("views", jsonviews);
				Logger.verb("DEMO", "Current Node: " + n.getWindow().toString());
				Collection<NObjectNode> views = guiHierarchy.get(n.getWindow());
				for (NObjectNode view : views) {
					Collection<HandlerBean> handlers = widgetToHandlers.get(view);
					Logger.verb("DEMO", "View: " + view + " handler: " + handlers);
					JSONObject viewjson = new JSONObject();
					jsonviews.add(viewjson);
					viewjson.put("name", view.toString());
					JSONArray jsonhandlers = new JSONArray();
					viewjson.put("handlers", jsonhandlers);
					for (HandlerBean handlerBean : handlers) {
						JSONObject handlerjson = new JSONObject();
						jsonhandlers.add(handlerjson);
						handlerjson.put("event", handlerBean.getEvent().toString());
						JSONArray eventhandlers = new JSONArray();
						handlerjson.put("handlers", eventhandlers);
						for (SootMethod m : handlerBean.getHandlers()) {
							eventhandlers.add(m.toString());
						}
					}

					Collection<NIdNode> imageIds = widgetToImages.get(view);
					JSONArray images = new JSONArray();
					viewjson.put("images", images);
					for (NIdNode imageid : imageIds) {
						images.add(imageid.toString());
					}

				}

			}
			out.println(wins.toJSONString());
			out.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
