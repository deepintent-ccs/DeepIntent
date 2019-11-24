/*
 * ListenerSpecification.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.listener;

import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.MethodNames;
import presto.android.gui.JimpleUtil;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ListenerSpecification {

  public static final int UNKNOWN_POSITION = -1;

  Map<SootClass, Map<EventType, Set<ListenerRegistration>>> rawSpecs;

  Map<SootClass, Set<ListenerRegistration>> viewAndRegistrations;

  // From a GUI type to the sub-signatures of all possible SetListener calls
  Map<SootClass, Set<String>> viewAndRegistrationSubsigs;

  // From a registration subsig to the listener position
  Map<String, Integer> regAndListenerPositions;

  Map<String, Integer> handlerViewPositions;

  Map<Stmt, EventType> regAndEvents;

  // Set of listener types
  Set<SootClass> listeners;

  private final Hierarchy hier;
  private final JimpleUtil jimpleUtil;

  private static ListenerSpecification theInstance;

  private ListenerSpecification() {
    rawSpecs = Maps.newHashMap();
    viewAndRegistrations = Maps.newHashMap();
    viewAndRegistrationSubsigs = Maps.newHashMap();
    regAndListenerPositions = Maps.newHashMap();
    handlerViewPositions = Maps.newHashMap();
    regAndEvents = Maps.newHashMap();
    listeners = Sets.newHashSet();

    hier = Hierarchy.v();
    jimpleUtil = JimpleUtil.v();
    readFromSpecificationFile(Configs.listenerSpecFile);
  }

  public static synchronized ListenerSpecification v() {
    if (theInstance == null) {
      theInstance = new ListenerSpecification();
    }
    return theInstance;
  }

  public void saveRegAndEvents(Stmt regStmt, EventType type) {
    regAndEvents.put(regStmt, type);
  }

  public EventType lookupEventType(Stmt regStmt) {
    return regAndEvents.get(regStmt);
  }

  public Set<SootClass> getGUITypes() {
    return rawSpecs.keySet();
  }

  public Set<EventType> getEventTypes() {
    Set<EventType> types = Sets.newHashSet();
    for (Map<EventType, Set<ListenerRegistration>> typeAndRegs : rawSpecs.values()) {
      types.addAll(typeAndRegs.keySet());
    }
    return types;
  }

  public Set<ListenerRegistration> getListenerRegistrations(SootClass guiType,
      EventType eventType) {
    Set<ListenerRegistration> result = Sets.newHashSet();
    for (SootClass candidateType : hier.getSupertypes(guiType)) {
      Map<EventType, Set<ListenerRegistration>> eventAndRegs =
          rawSpecs.get(candidateType);
      if (eventAndRegs == null || eventAndRegs.isEmpty()) {
        continue;
      }
      Set<ListenerRegistration> regs = eventAndRegs.get(eventType);
      if (regs != null) {
        result.addAll(regs);
      }
    }
    return result;
  }

  public ListenerRegistration getListenerRegistration(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    InvokeExpr ie = s.getInvokeExpr();
    if (!(ie instanceof InstanceInvokeExpr)) {
      return null;
    }
    SootMethod callee = ie.getMethod();
    String calleeSubsig = callee.getSubSignature();
    Local receiver = jimpleUtil.receiver(ie);
    Type type = receiver.getType();
    if (!(type instanceof RefType)) {
      return null;
    }
    SootClass receiverClass = ((RefType)type).getSootClass();
    for (SootClass candidateType : hier.getSupertypes(receiverClass)) {
      Set<ListenerRegistration> regSet = viewAndRegistrations.get(candidateType);
      if (regSet == null || regSet.isEmpty()) {
        continue;
      }
      for (ListenerRegistration reg : regSet) {
        if (reg.subsig.equals(calleeSubsig)) {
          return reg;
        }
      }
    }
    return null;
  }

  public Set<String> getRegistrationSubsigs(SootClass guiType) {
    Set<String> result = Sets.newHashSet();
    for (SootClass c : hier.getSupertypes(guiType)) {
      Set<String> subsigs = viewAndRegistrationSubsigs.get(c);
      if (subsigs != null && !subsigs.isEmpty()) {
        result.addAll(subsigs);
      }
    }

    return result;
  }

  public boolean containsRegistrationSubsig(SootClass guiType, String subsig) {
    for (SootClass c : hier.getSupertypes(guiType)) {
      Set<String> subsigs = viewAndRegistrationSubsigs.get(c);
      if (subsigs != null && subsigs.contains(subsig)) {
        return true;
      }
    }
    return false;
  }

  public boolean isListenerRegistration(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    if (!(ie instanceof InstanceInvokeExpr)) {
      return false;
    }
    Local receiver = jimpleUtil.receiver(ie);
    SootClass guiType = ((RefType)receiver.getType()).getSootClass();
    SootMethod callee = ie.getMethod();
    String subsig = callee.getSubSignature();
    return containsRegistrationSubsig(guiType, subsig);
  }

  public boolean isListenerType(SootClass c) {
    if(hier.getSupertypes(c) == null) {
      if (Configs.debugCodes.contains("isListenerDebug")) {
        System.out.println("[WARNING]: attempt to check if " + c + " is listener which " +
            "is not supposed to happen at runtime");
      }
			return false;
		}
    for (SootClass superTypeAndItself : hier.getSupertypes(c)) {
      if (listeners.contains(superTypeAndItself)) {
        return true;
      }
    }
    return false;
  }

  // Position of the listener parameter in the registration:
  //   InvokeExpr.getArg(...)
  public int getListenerPosition(String regSubsig) {
    Integer ret = regAndListenerPositions.get(regSubsig);
    if (ret == null) {
      return UNKNOWN_POSITION;
    }
    return ret.intValue();
  }

  public int getViewPositionInHandler(String handlerSubsig) {
    if (handlerSubsig.equals(MethodNames.onOptionsItemSelectedSubSig)
        || handlerSubsig.equals(MethodNames.onContextItemSelectedSubSig)) {
      return 0;
    }
    Integer result = handlerViewPositions.get(handlerSubsig);
    if (result == null) {
      return UNKNOWN_POSITION;
    } else {
      return result.intValue();
    }
  }

  // --- read & save the listener specs

  boolean saveToRawSpecs(SootClass guiType, EventType eventType,
      ListenerRegistration registration) {
    Map<EventType, Set<ListenerRegistration>> eventAndRegs = rawSpecs.get(guiType);
    if (eventAndRegs == null) {
      eventAndRegs = Maps.newHashMap();
      rawSpecs.put(guiType, eventAndRegs);
    }
    Set<ListenerRegistration> regs = eventAndRegs.get(eventType);
    if (regs == null) {
      regs = Sets.newHashSet();
      eventAndRegs.put(eventType, regs);
    }
    return regs.add(registration);
  }

  boolean saveToViewAndRegSubsigs(SootClass guiType, String registrationSubsig) {
    Set<String> subsigs = viewAndRegistrationSubsigs.get(guiType);
    if (subsigs == null) {
      subsigs = Sets.newHashSet();
      viewAndRegistrationSubsigs.put(guiType, subsigs);
    }
    return subsigs.add(registrationSubsig);
  }

  boolean saveToViewAndRegs(SootClass viewClasss, ListenerRegistration reg) {
    Set<ListenerRegistration> regSet = viewAndRegistrations.get(viewClasss);
    if (regSet == null) {
      regSet = Sets.newHashSet();
      viewAndRegistrations.put(viewClasss, regSet);
    }
    return regSet.add(reg);
  }

  void readFromSpecificationFile(String fn) {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    Document doc;
    try {
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      doc = dBuilder.parse(fn);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    if (Configs.verbose) {
      System.out.println("--- reading " + fn + "\n");
    }

    NodeList guis = doc.getElementsByTagName("gui");
    for (int i = 0; i < guis.getLength(); i++) {
      Node gui = guis.item(i);
      String type = gui.getAttributes().getNamedItem("type").getNodeValue();
      SootClass guiClass = Scene.v().getSootClass(type);
      if (rawSpecs.containsKey(guiClass)) {
        throw new RuntimeException(
            "Class " + guiClass + " specified more than once!");
      }
//      System.out.println("GUI: " + type);
      if (guiClass.isPhantom()) {
//        System.out.println("  !!! Phantom !!!");
        continue;
      }
      NodeList events = gui.getChildNodes();
      for (int j = 0; j < events.getLength(); j++) {
        Node event = events.item(j);
        if (!event.getNodeName().equals("event")) {
          continue;
        }
        String eventName = event.getAttributes().getNamedItem("name").getNodeValue();
        EventType eventType = EventType.valueOf(eventName);
        NodeList registrations = event.getChildNodes();
        for (int k = 0; k < registrations.getLength(); k++) {
          Node reg = registrations.item(k);
          if (!reg.getNodeName().equals("registration")) {
            continue;
          }
          NamedNodeMap regAttributes = reg.getAttributes();
          String regSubsig = regAttributes.getNamedItem("subsig").getNodeValue();
          if (!guiClass.declaresMethod(regSubsig)) {
            continue;
          }
          saveToViewAndRegSubsigs(guiClass, regSubsig);

          SootMethod regMethod = guiClass.getMethod(regSubsig);
          int listenerPosition = Integer.parseInt(
              regAttributes.getNamedItem("position").getNodeValue());
          regAndListenerPositions.put(regSubsig, listenerPosition);

          SootClass listener =
              ((RefType)regMethod.getParameterType(listenerPosition)).getSootClass();
          listeners.add(listener);

          ListenerRegistration listenerRegistration = new ListenerRegistration(
              eventType, regSubsig, listenerPosition, listener);
          saveToViewAndRegs(guiClass, listenerRegistration);

          NodeList handlerNodes = reg.getChildNodes();
          for (int l = 0; l < handlerNodes.getLength(); l++) {
            Node handlerNode = handlerNodes.item(l);
            if (!handlerNode.getNodeName().equals("handler")) {
              continue;
            }
            NamedNodeMap handlerAttributes = handlerNode.getAttributes();
            String handlerSubsig =
                handlerAttributes.getNamedItem("subsig").getNodeValue();
            SootMethod handlerMethod = listener.getMethod(handlerSubsig);
            int viewPosition = Integer.parseInt(
                handlerAttributes.getNamedItem("position").getNodeValue());
            handlerViewPositions.put(handlerSubsig, viewPosition);
            SootClass view = null;
            if (viewPosition != UNKNOWN_POSITION) {
              view = ((RefType)handlerMethod.getParameterType(viewPosition)).getSootClass();
            }
            EventHandler handler = new EventHandler(handlerSubsig, viewPosition, view);
            listenerRegistration.addHandler(handler);
          } // handlers
          saveToRawSpecs(guiClass, eventType, listenerRegistration);
        } // registrations
      } // events
    } // GUIs

    if (Configs.verbose) {
      System.out.println("\n--- finished reading " + fn);
    }
  }
}
