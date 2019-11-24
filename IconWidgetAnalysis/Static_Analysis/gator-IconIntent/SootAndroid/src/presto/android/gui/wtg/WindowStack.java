/*
 * WindowStack.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

import java.util.List;
import java.util.Stack;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;

public class WindowStack {
  private Stack<NObjectNode> stack;
  private List<WTGEdge> path;

  public WindowStack(List<WTGEdge> path) {
    this.path = Lists.newArrayList(path);
    this.stack = simulateWindowStack(this.path);
  }

  public List<WTGEdge> getPath() {
    return Lists.newArrayList(path);
  }

  public Stack<NObjectNode> getWindowStack() {
    Stack<NObjectNode> newStack = new Stack<NObjectNode>();
    newStack.addAll(this.stack);
    return newStack;
  }
  
  /**
   * This function reports feasible edges based on the window stack it constructs.
   * Window stack is the stack data structure containing only
   * 1. activity.
   * 2. dialog
   * 3. context_menu
   * 4. options_menu
   * Callbacks on each wtg edge involve API calls triggering one or multiple windows.
   * To avoid creating infeasible path of back edges, algorithm is proposed to simulate the window
   * stack.
   * The basic idea is that the top element of window stack should match the edge target
   * Assumption: no multiple may-close windows appear on a stack
   *
   * @return next feasible edges
   */
  public List<WTGEdge> expandFeasibleEdge() {
    List<WTGEdge> feasibleEdges = Lists.newArrayList();
    if (path.isEmpty()) {
      return feasibleEdges;
    }
    Stack<NObjectNode> windowStack = this.stack;
    if (windowStack.isEmpty()) {
      // invalid path to the node
      return feasibleEdges;
    }
    WTGEdge lastEdge = path.get(path.size() - 1);
    WTGNode lastNode = lastEdge.getTargetNode();
    for (WTGEdge outEdge : lastNode.getOutEdges()) {
      Stack<NObjectNode> newStack = new Stack<NObjectNode>();
      newStack.addAll(windowStack);
      newStack = processEdge(newStack, outEdge);
      NObjectNode targetWindow = outEdge.getTargetNode().getWindow();
      if (!newStack.isEmpty() && newStack.peek() == targetWindow) {
        feasibleEdges.add(outEdge);
      }
    }
    return feasibleEdges;
  }
  
  public boolean isFeasibleEdge(WTGEdge e) {
    Stack<NObjectNode> windowStack = this.stack;
    if (windowStack.isEmpty()) {
      // invalid path to the node
      return false;
    }
    Stack<NObjectNode> newStack = new Stack<NObjectNode>();
    newStack.addAll(windowStack);
    newStack = processEdge(newStack, e);
    NObjectNode targetWindow = e.getTargetNode().getWindow();
    return !newStack.isEmpty() && newStack.peek() == targetWindow;
  }
  
  public void addEdge(WTGEdge e) {
    if (!isFeasibleEdge(e)) {
      return;
    }
    Stack<NObjectNode> windowStack = this.stack;
    windowStack = processEdge(windowStack, e);
    this.path.add(e);
  }
  
  public void removeLastEdge() {
    if (path.isEmpty()) {
      return;
    }
    path.remove(path.size()-1);
  }
  
  public WindowStack copy() {
    return new WindowStack(this.path);
  }
  
  private Stack<NObjectNode> simulateWindowStack(List<WTGEdge> trace) {
    Stack<NObjectNode> windowStack = new Stack<NObjectNode>();
    // assuming the very beginning node already exists in the stack
    windowStack.push(trace.get(0).getSourceNode().getWindow());
    for (WTGEdge staticEdge : trace) {
      windowStack = processEdge(windowStack, staticEdge);
      if (windowStack.isEmpty() || windowStack.peek() != staticEdge.getTargetNode().getWindow()) {
        windowStack.clear();
        return windowStack;
      }
    }
    return windowStack;
  }

  private Stack<NObjectNode> processEdge(
      Stack<NObjectNode> windowStack, WTGEdge staticEdge) {
    Preconditions.checkNotNull(windowStack,
        "[Error]: initial window stack shouldn't be null");
    Preconditions.checkNotNull(staticEdge,
        "[Error]: edge to be processed shouldn't be null");
    for (StackOperation stackOp : staticEdge.getStackOps()) {
      NObjectNode opWindow = stackOp.getWindow();
      if (stackOp.isPushOp()) {
        windowStack.push(opWindow);
      } else {
        boolean found = false;
        while (!windowStack.isEmpty()) {
          NObjectNode topWindow = windowStack.pop();
          if (topWindow == opWindow) {
            found = true;
            break;
          }
        }
        if (!found) {
          return windowStack;
        }
      }
    }
    return windowStack;
  }
}
