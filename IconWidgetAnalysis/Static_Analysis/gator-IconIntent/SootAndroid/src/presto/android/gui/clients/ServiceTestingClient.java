package presto.android.gui.clients;

import com.google.common.collect.Lists;
import presto.android.*;
import presto.android.Hierarchy;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;


import presto.android.gui.wtg.util.WTGUtil;

import presto.android.xml.XMLParser;
import soot.*;
import soot.Scene;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by zero on 4/18/16.
 */
@SuppressWarnings("unused")
public class ServiceTestingClient implements GUIAnalysisClient{

  private List<SootClass> serviceClassList = null;

  private List<SootClass> servicesInManifest = null;

  private List<SootClass> intentServiceList = null;

  private XMLParser xml;

  public static SootClass serviceClass = Scene.v().getSootClass("android.app.Service");

  public static SootClass intentServiceClass = Scene.v().getSootClass("android.app.IntentService");

  @Override
  public void run(GUIAnalysisOutput output) {
    Collection<SootClass> appClassList = Scene.v().getApplicationClasses();
    serviceClassList = Lists.newArrayList();
    intentServiceList = Lists.newArrayList();
    servicesInManifest = Lists.newArrayList();
    xml = XMLParser.Factory.getXMLParser();

    for (Iterator<String> curStrIt = xml.getServices(); curStrIt.hasNext();){
      String curServiceClass = curStrIt.next();
      SootClass curCls = Scene.v().getSootClass(curServiceClass);
      if (!curCls.isConcrete()) {
        Logger.verb("SERVICE", "Service " + curCls.toString() + " is not Concrete");
      }
      servicesInManifest.add(curCls);
    }

    for (SootClass sc : appClassList) {
      if (Hierarchy.v().isSubclassOf(sc, serviceClass)){
        if (Configs.verbose) {
          Logger.verb("SERVICE", "Service Class: " + sc.toString());
        }
        serviceClassList.add(sc);
      }
      if (Hierarchy.v().isSubclassOf(sc, intentServiceClass)){
        if (Configs.verbose) {
          Logger.verb("SERVICE", "IntentService Class: " + sc.toString());
        }
        intentServiceList.add(sc);
      }
    }

    Logger.verb("SERVICE", "Service in Manifest: ");
    for (SootClass sc : servicesInManifest){
      Logger.verb("SERVICE", "\t" + sc.toString());
    }

    Logger.verb("SERVICE", "Service in Application Class: ");
    for (SootClass sc : serviceClassList){
      Logger.verb("SERVICE", "\t" + sc.toString());
    }

    Logger.verb("SERVICE", "IntentService in Application Class: ");
    for (SootClass sc : intentServiceList){
      Logger.verb("SERVICE", "\t" + sc.toString());
    }

    for (SootClass sc : appClassList) {
      List<SootMethod> smList = sc.getMethods();
      for (SootMethod sm : smList) {
        seekServiceOps(sm);
      }
    }




  }

  private void seekServiceOps(SootMethod sm) {

    //ExceptionalUnitGraph uGraph = new ExceptionalUnitGraph(sB);
    //sB.get
    if (sm.isPhantom()){
      Logger.verb("ServiceTestingWarning", sm.toString() + " is phantom");
      return;
    }

    if (sm.isAbstract()){
      Logger.verb("ServiceTestingWarning", sm.toString() + " is abstract");
      return;
    }

    if (!sm.isConcrete()) {
      Logger.verb("ServiceTestingWarning", sm.toString() + " is not concrete");
      return;
    }

    Body  sB = sm.retrieveActiveBody();

    ArrayList<Unit> unitList = Lists.newArrayList(sB.getUnits());
    for (Unit u: unitList) {
      if (u instanceof Stmt) {
        if (WTGUtil.v().isServiceRelated((Stmt)u)){
          Logger.verb("ServiceTesting","Hit: " + u.toString());
          Logger.verb("ServiceTesting","\t IN: " + sm.toString());

        }
      }
    }
  }



}
