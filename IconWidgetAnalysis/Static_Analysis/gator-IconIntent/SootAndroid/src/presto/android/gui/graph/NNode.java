/*
 * NNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import soot.jimple.Stmt;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public abstract class NNode {
	public static boolean verbose = false;
	public static int nextId = 0;
	public static int numberOfEdges = 0;
	public int id;

	// The flow graph node representing the widget id - could be NWidgetIdNode,
	// or null for nodes without ids (or sometimes, weirdly, NLayoutIdNode)
	public NIdNode idNode;

	// The displayed text for this GUI object. Right now, only valid for the
	// NInflNode nodes representing menu items.
	private Set<NNode> textNodes;
	private Set<NNode> drawableNodes;

	public NNode() {
		nextId++;
		id = nextId;
	}

	// NOTE(tony): "alias" nodes/paths
	protected ArrayList<NNode> succ;
	public ArrayList<Stmt> succSites;
	protected ArrayList<NNode> pred;
	public ArrayList<Stmt> predSites;

	// Anyone whose 'parent' is this obj. Used only in
	// NViewAllocNode, NInflNode, and NActivityNode
	protected Set<NNode> children;
	protected Set<NNode> parents;

	public synchronized Collection<NNode> getSuccessors() {
		if (succ == null || succ.isEmpty()) {
			return Collections.emptyList();
		} else {
			return Lists.newArrayList(succ);
		}
	}

	public synchronized int getNumberOfSuccessors() {
		return (succ == null ? 0 : succ.size());
	}

	public synchronized NNode getSuccessor(int index) {
		return succ.get(index);
	}

	public synchronized Collection<NNode> getPredecessors() {
		if (pred == null || pred.isEmpty()) {
			return Collections.emptyList();
		} else {
			return Lists.newArrayList(pred);
		}
	}

	public synchronized int getNumberOfPredecessors() {
		return (pred == null ? 0 : pred.size());
	}

	public synchronized NNode getPredecessor(int index) {
		return pred.get(index);
	}

	public synchronized boolean hasChild(NNode child) {
		if (children == null) {
			return false;
		}
		return children.contains(child);
	}

	public synchronized Iterator<NNode> getParents() {
		if (parents == null || parents.isEmpty()) {
			return Iterators.emptyIterator();
		} else {
			return parents.iterator();
		}
	}

	public synchronized Set<NNode> getChildren() {
		if (children == null) {
			return Collections.emptySet();
		} else {
			return children;
		}
	}

	public synchronized void removeEdgeTo(NNode target) {
		if (succ != null && succ.contains(target)) {
			succ.remove(target);
			numberOfEdges--;
		} else {
			if (target.pred != null && target.pred.contains(this)) {
				throw new RuntimeException("Broken edge " + this + "===>" + target);
			}
			return;
		}
		if (target.pred == null || !target.pred.contains(this)) {
			throw new RuntimeException("Broken edge " + this + "===>" + target);
		}
		target.pred.remove(this);
	}

	public synchronized void addEdgeTo(NNode x) {
		addEdgeTo(x, null);
	}

	public synchronized void addEdgeTo(NNode x, Stmt s) {
		if (succ == null) {
			succ = Lists.newArrayListWithCapacity(4);
			succSites = Lists.newArrayListWithCapacity(4);
		}
		if (!succ.contains(x)) {
			succ.add(x);
			numberOfEdges++;
		} else {
			return;
		}
		succSites.add(s);
		if (verbose) {
			if (s == null) {
				System.out.println(this + " ==> " + x);
			} else {
				System.out.println(this + " ==> " + x + " [" + s + "]");
			}
		}
		// predecessors
		if (x.pred == null) {
			x.pred = Lists.newArrayListWithCapacity(4);
			x.predSites = Lists.newArrayListWithCapacity(4);
		}
		if (x.pred.contains(this)) {
			throw new RuntimeException();
		}
		x.pred.add(this);
		x.predSites.add(s);
	}

	public synchronized void addParent(NNode p) {
		if (p == this) {
			throw new RuntimeException("p.addView(p) for " + p);
		}
		if (p == null) {
			return;
		}
		if (parents == null) {
			parents = Sets.newHashSetWithExpectedSize(1);
		}
		parents.add(p);
		if (verbose) {
			System.out.println(this + " [p]==> " + p);
		}
		if (p.children == null) {
			p.children = Sets.newHashSet();
		}
		p.children.add(this);
	}

	public synchronized boolean addTextNode(NNode text) {
		if (textNodes == null) {
			textNodes = Sets.newHashSet();
		}
		return textNodes.add(text);
	}

	public synchronized boolean addDrawableNode(NNode drawable) {
		if (drawableNodes == null) {
			drawableNodes = Sets.newHashSet();
		}
		return drawableNodes.add(drawable);
	}

	public synchronized Iterator<NNode> getTextNodes() {
		if (textNodes == null) {
			return Iterators.emptyIterator();
		}
		return textNodes.iterator();
	}
}
