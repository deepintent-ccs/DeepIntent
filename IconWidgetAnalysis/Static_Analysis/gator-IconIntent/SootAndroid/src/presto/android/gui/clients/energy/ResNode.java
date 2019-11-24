package presto.android.gui.clients.energy;

import presto.android.Logger;
import presto.android.gui.clients.energy.EnergyResourceType;
import presto.android.gui.graph.NObjectNode;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;

/**
 * Created by zero on 7/30/15.
 */
public class ResNode {
  //Back Reached node
  public NObjectNode objectNode;
  //Statement that request or release the resourece
  public Stmt stmt;
  //The handler/callbacks request/release the resourece
  public SootMethod handler;
  //The method contains the Stmt
  public SootMethod context;
  //GUIwidget of the WTGEdge. Currently not used.
  public NObjectNode guiWidget;

  public ResNode(NObjectNode window, Stmt stmt, SootMethod handler, SootMethod context){
    this.objectNode = window;
    this.stmt = stmt;
    this.handler = handler;
    this.context = context;

  }

  @Override
  public boolean equals(Object o1){
    if (o1 instanceof ResNode){
      ResNode rO = (ResNode) o1;
      if (this.objectNode.equals(rO.objectNode) &&
              this.context.equals(rO.context) &&
              this.stmt.equals(rO.stmt) &&
              this.handler.equals(rO.handler))
        return true;
    }

    return false;
  }

  @Override
  public int hashCode(){
    long hash = 0;
    if (this.objectNode != null )
      hash = this.objectNode.hashCode();
    hash += this.context.hashCode() + this.stmt.hashCode() + this.handler.hashCode();
    hash = hash * 7 % 2147483647;
    return (int)hash;
  }

  //Determine if it is a Google Maps framework/Android LocationManager
  public EnergyResourceType.resType getUnitType(){
    InvokeStmt curIvk = (InvokeStmt) stmt;
    SootClass curClz = curIvk.getInvokeExpr().getMethod().getDeclaringClass();
    SootMethod curMethod = curIvk.getInvokeExpr().getMethod();
    EnergyResourceType.resType curType = EnergyResourceType.v().classMethodTypeToResTyoe(curClz, curMethod);
    return curType;

  }

  public boolean compare(NObjectNode objectNode, Stmt stmt, SootMethod handler, SootMethod context) {
    final String mtdTag = "RESCMP";
    if (false) {
      Logger.verb(mtdTag, "src: "+ this.objectNode + " " + this.stmt + " " + this.context + this.handler);
      Logger.verb(mtdTag, "target: " + objectNode + " " + stmt + " " + handler + " " + context);
    }
    if (this.objectNode != objectNode)
      return false;

    if (this.stmt != stmt) {
      return false;
    }

    if (this.handler != handler) {
      return false;
    }

    if (this.context != context) {
      return false;
    }

    return true;
  }

  public boolean compare(NObjectNode objectNode, Stmt stmt, SootMethod context) {
    final String mtdTag = "RESCMP";
    if (false) {
      Logger.verb(mtdTag, "src: "+ this.objectNode + " " + this.stmt + " " + this.context + this.handler);
      Logger.verb(mtdTag, "target: " + objectNode + " " + stmt + " " + handler + " " + context);
    }
    if (this.objectNode != objectNode)
      return false;

    if (this.stmt != stmt) {
      return false;
    }


    if (this.context != context) {
      return false;
    }

    return true;
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();
    sb.append("ObjectNode: ");
    if (this.objectNode != null)
      sb.append(this.objectNode);
    sb.append(" ");
    sb.append("Stmt: ");
    sb.append(this.stmt);
    sb.append("Context: ");
    sb.append(this.context);
    sb.append("Handler: ");
    sb.append(this.handler);
    return sb.toString();
  }

}
