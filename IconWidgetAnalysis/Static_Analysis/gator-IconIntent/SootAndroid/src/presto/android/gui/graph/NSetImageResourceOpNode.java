package presto.android.gui.graph;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

//SetImageResource: view.setImageResource(R.drawable.image)
public class NSetImageResourceOpNode extends NOpNode {

	public NSetImageResourceOpNode(NNode idNode, NNode receiverNode, Pair<Stmt, SootMethod> callSite,
			boolean artificial) {
		super(callSite, artificial);
		idNode.addEdgeTo(this);
	    receiverNode.addEdgeTo(this);
	}

	@Override
	public NVarNode getReceiver() {
		return (NVarNode) this.pred.get(1);
	}

	@Override
	public NNode getParameter() {
		return this.pred.get(0);
	}

	@Override
	public boolean hasReceiver() {
		return true;
	}

	@Override
	public boolean hasParameter() {
		return true;
	}

	// no getLhs()
	@Override
	public boolean hasLhs() {
		return false;
	}

}
