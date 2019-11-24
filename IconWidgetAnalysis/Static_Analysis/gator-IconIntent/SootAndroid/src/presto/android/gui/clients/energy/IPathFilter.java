package presto.android.gui.clients.energy;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.ds.WTGEdge;

import java.util.List;
import java.util.Stack;

/**
 * Created by zero on 2/15/16.
 */
public interface IPathFilter {

  /***
   * Specify the stop rule for the DFS traversal
   * @param P
   * @param S
   * @return
   */
  boolean match(List<WTGEdge> P, Stack<NObjectNode> S);

  /***
   * Return the name of the filter
   * @return the name of the filter.
   */
  String getFilterName();

}
