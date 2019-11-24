/*
 * WTGBuilder.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Debug;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.algo.BackEdgeBuilder;
import presto.android.gui.wtg.algo.CallbackSequenceBuilder;
import presto.android.gui.wtg.algo.CloseWindowEdgeBuilder;
import presto.android.gui.wtg.algo.ExplicitForwardEdgeBuilder;
import presto.android.gui.wtg.algo.LifecycleCloseEdgeBuilder;
import presto.android.gui.wtg.algo.LifecycleForwardEdgeBuilder;
import presto.android.gui.wtg.analyzer.CFGAnalyzer;
import presto.android.gui.wtg.ds.HandlerBean;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;

public class WTGBuilder {
	// gui analysis output
	private GUIAnalysisOutput guiOutput;

	private FlowgraphRebuilder flowgraphRebuilder;

	// wtg edge analyzer
	@SuppressWarnings("unused")
	private CFGAnalyzer cfgAnalyzer;
	// wtg
	private WTG wtg;
	// output of each stage
	private List<Multimap<WTGEdgeSig, WTGEdge>> stageOutput;

	public Multimap<NObjectNode, NObjectNode> guiHierarchy;

	public Multimap<NObjectNode, HandlerBean> widgetToHandlers;

	private String apkname;

	public Multimap<NObjectNode, NIdNode> widgetToImages;

	public void build(GUIAnalysisOutput output) {
		preBuild(output);
		building();
		postBuild();
	}

	public WTGBuilder() {
		wtg = new WTG();
		stageOutput = Lists.newArrayList();
	}

	public WTGBuilder(String apkname) {
		this();
		this.apkname = apkname;
		wtg.apkname = apkname;
	}

	WTG getWTG() {
		return wtg;
	}

	private void building() {
		Multimap<WTGNode, NActivityNode> ownership = HashMultimap.create();
		ExplicitForwardEdgeBuilder explicitForwardEdgeBuilder = new ExplicitForwardEdgeBuilder(guiOutput,
				flowgraphRebuilder);
		Multimap<WTGEdgeSig, WTGEdge> stage1 = explicitForwardEdgeBuilder.buildEdges(wtg);
		Logger.verb(getClass().getSimpleName(), "stage 1 finishes");
		Multimap<WTGEdgeSig, WTGEdge> stage2 = new LifecycleForwardEdgeBuilder(guiOutput, flowgraphRebuilder)
				.buildEdges(wtg, stage1, ownership);
		Logger.verb(getClass().getSimpleName(), "stage 2 finishes");
		Multimap<WTGEdgeSig, WTGEdge> stage3 = new CloseWindowEdgeBuilder(guiOutput, flowgraphRebuilder).buildEdges(wtg,
				stage2, ownership);
		Logger.verb(getClass().getSimpleName(), "stage 3 finishes");
		Multimap<WTGEdgeSig, WTGEdge> stage4 = new CallbackSequenceBuilder(guiOutput, flowgraphRebuilder)
				.buildEdges(wtg, stage3, ownership);
		Logger.verb(getClass().getSimpleName(), "stage 4 finishes");
		Multimap<WTGEdgeSig, WTGEdge> stage5 = new BackEdgeBuilder(guiOutput, flowgraphRebuilder).buildEdges(wtg,
				stage4, ownership);
		Logger.verb(getClass().getSimpleName(), "stage 5 finishes");
		Multimap<WTGEdgeSig, WTGEdge> stage6 = new LifecycleCloseEdgeBuilder(guiOutput, flowgraphRebuilder)
				.buildEdges(wtg, stage5, ownership);
		Logger.verb(getClass().getSimpleName(), "stage 6 finishes");

		// store the result for all stages
		stageOutput.add(stage1);
		stageOutput.add(stage2);
		stageOutput.add(stage3);
		stageOutput.add(stage4);
		stageOutput.add(stage5);
		stageOutput.add(stage6);
		// construct wtg
		Multimap<WTGEdgeSig, WTGEdge> finalStage = stageOutput.get(stageOutput.size() - 1);
		for (WTGEdgeSig sig : finalStage.keySet()) {
			WTGEdge edge = sig.getEdge();
			wtg.addEdge(edge);
		}
		// resurrect the edges
		ignoreEdges(stageOutput);
		this.guiHierarchy = explicitForwardEdgeBuilder.getGUIHierarchy();
		this.widgetToHandlers = explicitForwardEdgeBuilder.getWidgetToHandlers();
		this.widgetToImages = explicitForwardEdgeBuilder.getWidgetToImages();
	}

	// do initialisation stuff, e.g., rebuild flow graph
	private void preBuild(GUIAnalysisOutput output) {
		Preconditions.checkNotNull(output, "[Error]: guiOutput is null");
		// init
		guiOutput = output;
		// rebuild flowgraph
		flowgraphRebuilder = FlowgraphRebuilder.v(guiOutput);
		// initialize wtg edge builder
		cfgAnalyzer = new CFGAnalyzer(guiOutput, flowgraphRebuilder);
	}

	// post build
	private void postBuild() {
		if (Configs.debugCodes.contains(Debug.DUMP_CCFX_DEBUG)) {
			wtg.dump();
		}
	}

	private void ignoreEdges(List<Multimap<WTGEdgeSig, WTGEdge>> stageOutput) {
		if (stageOutput.isEmpty()) {
			return;
		}
		Set<WTGEdge> aliveEdges = Sets.newHashSet();
		Multimap<WTGEdgeSig, WTGEdge> finalStage = stageOutput.get(stageOutput.size() - 1);
		for (WTGEdgeSig newEdge : finalStage.keySet()) {
			aliveEdges.add(newEdge.getEdge());
		}

		Set<WTGEdge> nextAliveEdges = Sets.newHashSet();
		for (int i = stageOutput.size() - 1; i >= 0; i--) {
			Set<WTGEdgeSig> deadEdges = Sets.newHashSet();
			Multimap<WTGEdgeSig, WTGEdge> stage = stageOutput.get(i);
			for (WTGEdgeSig newEdge : stage.keySet()) {
				if (aliveEdges.contains(newEdge.getEdge())) {
					nextAliveEdges.addAll(stage.get(newEdge));
				} else {
					deadEdges.add(newEdge);
				}
			}
			for (WTGEdgeSig deadEdge : deadEdges) {
				stage.removeAll(deadEdge);
			}

			aliveEdges.clear();
			Set<WTGEdge> tmp = nextAliveEdges;
			nextAliveEdges = aliveEdges;
			aliveEdges = tmp;
		}
	}

	/**
	 * This method is used to generate Table II in ASE paper.
	 */
	@SuppressWarnings("unused")
	private void collectStatistic(WTGBuilder wtgBuilder, WTGAnalysisOutput wtgOutput) {
		Preconditions.checkArgument(wtgBuilder instanceof WTGBuilder);
		long execTime = Debug.v().getExecutionTime();
		WTG wtg = wtgOutput.getWTG();
		int numOfNodes = wtg.getNodes().size();
		// the output of each stage is a data structure mapping the signature of
		// each input
		// edge to multiple output edges that are generated because of the input
		// edge
		// in section III of ASE'15 paper, 3-stages algorithm is defined attempt
		// to construct
		// WTG. In the actual implementation, there are 6 micro stages.
		// 1'st stage described in the paper is associated with micro stages 1
		// and 2
		// 2'nd stage in the paper corresponds to micro stages 3 and 4
		// 3'rd stage is represented by micro stages 5 and 6
		Multimap<WTGEdgeSig, WTGEdge> stage2 = wtgBuilder.getStageOutput(2);
		Multimap<WTGEdgeSig, WTGEdge> stage4 = wtgBuilder.getStageOutput(4);
		Multimap<WTGEdgeSig, WTGEdge> stage6 = wtgBuilder.getStageOutput(6);
		// print the numbers for Table II in the paper with k is specified
		// through "-succDepth" command line
		Logger.verb(getClass().getSimpleName(),
				"\\texttt{" + Configs.benchmarkName + "}\t& nodes: " + numOfNodes + "\t& stage2: " + stage2.size()
						+ "\t& stage4: " + stage4.size() + "(" + diffPartialEdge(stage2, stage4) + ")" + "\t& stage6: "
						+ stage6.size() + "(" + diffFullEdge(stage4, stage6) + ")" + "\t& " + Configs.sDepth + "\t& "
						+ execTime);
	}

	private Multimap<WTGEdgeSig, WTGEdge> getStageOutput(int index) {
		return stageOutput.get(index - 1);
	}

	// diff the signatures of two sets of edges, by comparing all their
	// attributes, including
	// source, target, push/pop label sequence and etc.
	private String diffFullEdge(Multimap<WTGEdgeSig, WTGEdge> first, Multimap<WTGEdgeSig, WTGEdge> second) {
		int adds = 0, removes = 0;
		for (WTGEdgeSig sig : first.keySet()) {
			if (!second.containsKey(sig)) {
				removes++;
			}
		}
		for (WTGEdgeSig sig : second.keySet()) {
			if (!first.containsKey(sig)) {
				adds++;
			}
		}
		if (first.size() - removes + adds != second.size()) {
			Logger.err(getClass().getSimpleName(), "edges don't match");
		} else if (adds < removes) {
			Logger.err(getClass().getSimpleName(), "removes: " + removes + " is larger than adds: " + adds);
		}
		return "-" + removes + "/" + "+" + adds;
	}

	// diff the signatures of two sets of edges, by comparing only part of their
	// attributes, e.g., source,
	// target and event handling callbacks. The reason it is needed because
	// edges generated from stage 1
	// (defined in the paper) do not contain push/pop labels, but edges
	// generated in stage 2 do have such
	// labels. Thus, to compare these two sets of edges, we choose to do partial
	// diff
	private String diffPartialEdge(Multimap<WTGEdgeSig, WTGEdge> first, Multimap<WTGEdgeSig, WTGEdge> second) {
		int adds = 0;
		{
			Set<WTGEdgeSig> newSigs = Sets.newHashSet();
			for (WTGEdgeSig oldSig : first.keySet()) {
				boolean match = false;
				for (WTGEdgeSig newSig : second.keySet()) {
					if (oldSig.getSourceNode() == newSig.getSourceNode()
							&& oldSig.getTargetNode() == newSig.getTargetNode()
							&& oldSig.getWTGHandlers().equals(newSig.getWTGHandlers()) && !newSigs.contains(newSig)) {
						match = true;
						newSigs.add(newSig);
						break;
					}
				}
				if (!match) {
					Logger.err(getClass().getSimpleName(), "edges shouldn't be removed");
				}
			}
		}
		{
			Set<WTGEdgeSig> oldSigs = Sets.newHashSet();
			for (WTGEdgeSig newSig : second.keySet()) {
				boolean match = false;
				for (WTGEdgeSig oldSig : first.keySet()) {
					if (oldSig.getSourceNode() == newSig.getSourceNode()
							&& oldSig.getTargetNode() == newSig.getTargetNode()
							&& oldSig.getWTGHandlers().equals(newSig.getWTGHandlers()) && !oldSigs.contains(oldSig)) {
						match = true;
						oldSigs.add(oldSig);
						break;
					}
				}
				if (!match) {
					adds++;
				}
			}
		}
		if (first.size() + adds != second.size()) {
			Logger.err(getClass().getSimpleName(), "edges don't match");
		}
		return "-" + 0 + "/" + "+" + adds;
	}
}
