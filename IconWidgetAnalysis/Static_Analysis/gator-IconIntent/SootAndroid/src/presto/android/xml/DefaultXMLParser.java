/*
 * DefaultXMLParser.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.google.common.collect.Sets;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.xml.XMLParser.AbstractXMLParser;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/*
 * This is a re-design of the xml parsing component.
 */
class DefaultXMLParser extends AbstractXMLParser {
	@Override
	public Integer getSystemRIdValue(String idName) {
		return lookupIdInGeneralMap("id", idName, true);
	}

	@Override
	public Integer getSystemRLayoutValue(String layoutName) {
		return lookupIdInGeneralMap("layout", layoutName, true);
	}

	@Override
	public String getApplicationRLayoutName(Integer value) {
		return lookupNameInGeneralMap("layout", value, false);
	}

	@Override
	public String getSystemRLayoutName(Integer value) {
		return lookupNameInGeneralMap("layout", value, true);
	}

	@Override
	public AndroidView findViewById(Integer id) {
		AndroidView res = id2View.get(id);
		if (res != null) {
			return res;
		}

		res = sysId2View.get(id);
		if (res != null) {
			return res;
		}

		res = extraId2ViewMap.get(id);
		if (res != null) {
			return res;
		}
		return null;
	}

	@Override
	public Set<Integer> getApplicationLayoutIdValues() {
		return invRGeneralIdMap.get("layout").keySet();
	}

	@Override
	public Set<Integer> getSystemLayoutIdValues() {
		return invSysRGeneralIdMap.get("layout").keySet();
	}

	@Override
	public Set<Integer> getApplicationMenuIdValues() {
		return invRGeneralIdMap.get("menu").keySet();
	}

	@Override
	public Set<Integer> getSystemMenuIdValues() {
		return invSysRGeneralIdMap.get("menu").keySet();
	}

	@Override
	public String getApplicationRMenuName(Integer value) {
		return lookupNameInGeneralMap("menu", value, false);
	}

	@Override
	public String getSystemRMenuName(Integer value) {
		return lookupNameInGeneralMap("menu", value, true);
	}

	@Override
	public Set<Integer> getApplicationRIdValues() {
		Set<Integer> retSet = Sets.newHashSet();
		retSet.addAll(invRGeneralIdMap.get("id").keySet());
		retSet.addAll(extraId2ViewMap.keySet());
		return retSet;
	}

	@Override
	public Set<Integer> getSystemRIdValues() {
		return invSysRGeneralIdMap.get("id").keySet();
	}

	@Override
	public String getApplicationRIdName(Integer value) {
		return lookupNameInGeneralMap(value, false);
	}

	@Override
	public String getSystemRIdName(Integer value) {
		return lookupNameInGeneralMap("id", value, true);
	}

	@Override
	public Set<Integer> getStringIdValues() {
		return invRGeneralIdMap.get("string").keySet();
	}

	@Override
	public String getRStringName(Integer value) {
		return lookupNameInGeneralMap("string", value, false);
	}

	@Override
	public String getStringValue(Integer idValue) {
		return intAndStringValues.get(idValue);
	}

	@Override
	public Iterator<String> getServices() {
		return services.iterator();
	}

	// ================================================

	private static final boolean debug = false;

	private static DefaultXMLParser theInst;

	private DefaultXMLParser() {
		doIt();
	}

	static synchronized DefaultXMLParser v() {
		if (theInst == null) {
			theInst = new DefaultXMLParser();
		}
		return theInst;
	}

	// === implementation details
	private void doIt() {
		rGeneralIdMap = Maps.newHashMap();
		invRGeneralIdMap = Maps.newHashMap();
		sysRGeneralIdMap = Maps.newHashMap();
		invSysRGeneralIdMap = Maps.newHashMap();

		readManifest();

		readRFile();
		// If the XML parser is working in ApkMode,
		// Read the public.xml as well.
		if (Configs.apkMode) {
			readPublicXML();
		}

		// Strings must be read first
		readStrings();

		// Then, layout and menu. Later, we may need to read preference as well.
		readLayout();
		readMenu();

		// debugPrintAll();

	}

	private void readPublicXML() {
		String fn = Configs.resourceLocation + "/values/public.xml";
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fn);

			Node root = doc.getElementsByTagName("resources").item(0);
			NodeList children = root.getChildNodes();

			for (int i = 0; i < children.getLength(); i++) {
				Node curNode = children.item(i);
				if (curNode.getNodeName().equals("public")) {
					NamedNodeMap attrMap = curNode.getAttributes();
					Node typeAttr = attrMap.getNamedItem("type");
					Node nameAttr = attrMap.getNamedItem("name");
					Node idAttr = attrMap.getNamedItem("id");
					if (typeAttr == null || nameAttr == null || idAttr == null) {
						Logger.verb("XML", "PublicXML: attributes contain null ");
						continue;
					}

					String typeStr = typeAttr.getTextContent();
					String nameStr = nameAttr.getTextContent();
					String idStr = idAttr.getTextContent();
					feedIdIntoGeneralMap(typeStr, nameStr, Integer.decode(idStr), false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void readManifest() {
		String fn = Configs.manifestLocation;
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fn);
			Node root = doc.getElementsByTagName("manifest").item(0);
			appPkg = root.getAttributes().getNamedItem("package").getTextContent();

			Node appNode = doc.getElementsByTagName("application").item(0);
			NodeList nodes = appNode.getChildNodes();
			for (int i = 0; i < nodes.getLength(); ++i) {
				try {
					Node n = nodes.item(i);
					String eleName = n.getNodeName();
					if ("activity".equals(eleName)) {
						NamedNodeMap m = n.getAttributes();
						String cls = Helper.getClassName(m.getNamedItem("android:name").getTextContent(), appPkg);
						if (cls == null) {
							continue;
						}
						activities.add(cls);

						if (isMainActivity(n)) {
							assert mainActivity == null;
							mainActivity = Scene.v().getSootClass(cls);
						}

						ActivityLaunchMode launchMode = ActivityLaunchMode.standard;
						Node launchModeNode = m.getNamedItem("android:launchMode");
						if (launchModeNode != null) {
							launchMode = ActivityLaunchMode.valueOf(launchModeNode.getTextContent());
						}
						activityAndLaunchModes.put(cls, launchMode);
					}

					if ("service".equals(eleName)) {
						NamedNodeMap m = n.getAttributes();
						String partialClassName = m.getNamedItem("android:name").getTextContent();

						String cls = Helper.getClassName(partialClassName, appPkg);
						services.add(cls);

						if (Configs.verbose) {
							Logger.verb("XML", "Service: " + cls);
						}
					}
				} catch (NullPointerException ne) {
					// A work around for uk.co.busydoingnothing.catverbs_5.apk
					Logger.verb("ERROR",
							"Nullpointer Exception in readManifest maybe caused by " + "customized namespace");
					continue;
				}
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void retriveIntentFilters(Node node) {
		NodeList list = node.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			String s = n.getNodeName();

			if (!s.equals("intent-filter"))
				continue;

		}
	}

	private boolean isMainActivity(Node node) {
		assert "activity".equals(node.getNodeName());
		NodeList list = node.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			String s = n.getNodeName();
			if (!s.equals("intent-filter")) {
				continue;
			}
			if (isMainIntent(n)) {
				return true;
			}
		}
		return false;
	}

	private boolean isMainIntent(Node node) {
		assert "intent-filter".equals(node.getNodeName());
		boolean isMain = false;
		boolean isLauncher = false;
		NodeList list = node.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			String s = n.getNodeName();
			if ("action".equals(s)) {
				NamedNodeMap m = n.getAttributes();
				String action = m.getNamedItem("android:name").getTextContent();
				if ("android.intent.action.MAIN".equals(action)) {
					isMain = true;
				}
			} else if ("category".equals(s)) {
				NamedNodeMap m = n.getAttributes();
				String category = m.getNamedItem("android:name").getTextContent();
				if ("android.intent.category.LAUNCHER".equals(category)) {
					isLauncher = true;
				}
			}
		}
		return isMain && isLauncher;
	}

	// --- END

	// --- R files
	private HashMap<String, HashMap<String, Integer>> rGeneralIdMap;
	private HashMap<String, HashMap<String, Integer>> sysRGeneralIdMap;
	private HashMap<String, HashMap<Integer, String>> invRGeneralIdMap;
	private HashMap<String, HashMap<Integer, String>> invSysRGeneralIdMap;

	// <int const val, string val in xml>
	private HashMap<Integer, String> intAndStringValues;
	// <R.string field, its string val>
	private HashMap<String, String> rStringAndStringValues;

	private final HashMap<Integer, String> sysIntAndStringValues = Maps.newHashMap();
	private final HashMap<String, String> sysRStringAndStringValues = Maps.newHashMap();

	private HashMap<Integer, AndroidView> extraId2ViewMap = Maps.newHashMap();

	public void feedIdIntoGeneralMap(String type, String name, Integer value, boolean isSys) {
		assert type != null;
		assert name != null;
		assert value != null;
		HashMap<String, Integer> workingMap;
		HashMap<Integer, String> invWorkingMap;
		if (isSys) {
			if (!sysRGeneralIdMap.containsKey(type)) {
				sysRGeneralIdMap.put(type, Maps.newHashMap());
			}
			if (!invSysRGeneralIdMap.containsKey(type)) {
				invSysRGeneralIdMap.put(type, Maps.newHashMap());
			}
			workingMap = sysRGeneralIdMap.get(type);
			invWorkingMap = invSysRGeneralIdMap.get(type);
		} else {
			if (!rGeneralIdMap.containsKey(type)) {
				rGeneralIdMap.put(type, Maps.newHashMap());
			}
			if (!invRGeneralIdMap.containsKey(type)) {
				invRGeneralIdMap.put(type, Maps.newHashMap());
			}
			workingMap = rGeneralIdMap.get(type);
			invWorkingMap = invRGeneralIdMap.get(type);
		}

		assert workingMap != null;
		assert invWorkingMap != null;

		if (Configs.verbose) {
			if (workingMap.containsKey(name) || invWorkingMap.containsKey(value)) {
				System.out.println(
						"[VERB] DefaultXML Parser feedIdIntoGeneralMap name value conflicts at " + name + ":" + value);
			}
		}
		workingMap.put(name, value);
		invWorkingMap.put(value, name);
	}

	private void readRFile() {

		rGeneralIdMap.put("id", Maps.newHashMap());
		rGeneralIdMap.put("layout", Maps.newHashMap());
		rGeneralIdMap.put("menu", Maps.newHashMap());
		rGeneralIdMap.put("string", Maps.newHashMap());
		invRGeneralIdMap.put("id", Maps.newHashMap());
		invRGeneralIdMap.put("layout", Maps.newHashMap());
		invRGeneralIdMap.put("menu", Maps.newHashMap());
		invRGeneralIdMap.put("string", Maps.newHashMap());

		sysRGeneralIdMap.put("id", Maps.newHashMap());
		sysRGeneralIdMap.put("layout", Maps.newHashMap());
		sysRGeneralIdMap.put("menu", Maps.newHashMap());
		sysRGeneralIdMap.put("string", Maps.newHashMap());
		invSysRGeneralIdMap.put("id", Maps.newHashMap());
		invSysRGeneralIdMap.put("layout", Maps.newHashMap());
		invSysRGeneralIdMap.put("menu", Maps.newHashMap());
		invSysRGeneralIdMap.put("string", Maps.newHashMap());

		for (SootClass cls : Lists.newArrayList(Scene.v().getClasses())) {
			// Read appPkg + .R$
			if (cls.getName().startsWith(appPkg + ".R$")) {
				int idx = cls.getName().indexOf("$");
				String type = cls.getName().substring(idx + 1);
				// Logger.verb("RFile", "matched app R " + type);
				readIntConstFields(cls.getName(), type, false);
			}
			// Read android.R$
			if (cls.getName().startsWith("android.R$")) {
				int idx = cls.getName().indexOf("$");
				String type = cls.getName().substring(idx + 1);
				// Logger.verb("RFile", "matched android R " + type);
				readIntConstFields(cls.getName(), type, true);
			}
			// Read com.android.internal.R$
			if (cls.getName().startsWith("com.android.internal.R$")) {
				int idx = cls.getName().indexOf("$");
				String type = cls.getName().substring(idx + 1);
				// Logger.verb("RFile", "matched android internal R " + type);
				if (!cls.isPhantom()) {
					readIntConstFields(cls.getName(), type, true);
				}
			}
		}

		final String internalSysRIdClass = "com.android.internal.R$id";
		// We are not going to rely on android.jar built from AOSP.
		// So read our own internal const files.
		ResourceConstantHelper.loadConstFromFile(this);
	}

	private void readIntConstFields(String clsName, String type, boolean isSys) {
		SootClass idCls = Scene.v().getSootClass(clsName);
		// This particular R$* class is not used. Should be system R class
		// though.
		if (idCls.isPhantom()) {
			// if (Configs.verbose) {
			if (true) {
				System.out.println("[DEBUG] " + clsName + " is phantom!");
			}
			return;
		}

		for (SootField f : idCls.getFields()) {
			try {
				String tag = f.getTag("IntegerConstantValueTag").toString();
				int val = Integer.parseInt(tag.substring("ConstantValue: ".length()));
				String name = f.getName();
				feedIdIntoGeneralMap(type, name, val, isSys);
			} catch (Exception e) {
				// There exist arrays in R file.
				// Ignore these arrays for now.
			}
		}
	}

	// --- END

	// --- read layout files
	private static final String ID_ATTR = "android:id";
	private static final String TEXT_ATTR = "android:text";
	private static final String TITLE_ATTR = "android:title";

	private static int nonRId = -0x7f040000;

	private HashMap<Integer, AndroidView> id2View;
	private HashMap<Integer, AndroidView> sysId2View;

	private void readLayout() {
		id2View = Maps.newHashMap();
		readLayout(Configs.resourceLocation + "/", invRGeneralIdMap.get("layout"), id2View, false);
		// readLayoutApplicationProject(invRLayoutMap, id2View);

		sysId2View = Maps.newHashMap();
		readLayout(Configs.sysProj + "/res/", invSysRGeneralIdMap.get("layout"), sysId2View, true);

		resolveIncludes(Configs.sysProj + "/res/", invSysRGeneralIdMap.get("layout"), sysId2View, true);
		resolveIncludes(Configs.resourceLocation + "/", invRGeneralIdMap.get("layout"), id2View, false);
		// resolveIncludesApplicationProject(invRLayoutMap, id2View);

	}

	// TODO: due to the way we implement resolveIncludes(), now we need
	// to change findViewById.
	private void resolveIncludes(String resRoot, HashMap<Integer, String> nameMap,
			HashMap<Integer, AndroidView> viewMap, boolean isSys) {

		HashMap<String, AndroidView> name2View = Maps.newHashMap();
		for (Map.Entry<Integer, String> entry : nameMap.entrySet()) {
			String name = entry.getValue();
			AndroidView view = viewMap.get(entry.getKey());
			name2View.put(name, view);
		}
		// boolean isSys = (viewMap == sysId2View);
		LinkedList<AndroidView> work = Lists.newLinkedList();
		work.addAll(viewMap.values());
		while (!work.isEmpty()) {
			AndroidView view = work.remove();
			for (int i = 0; i < view.getNumberOfChildren(); i++) {
				IAndroidView child = view.getChildInternal(i);
				if (child instanceof AndroidView) {
					work.add((AndroidView) child);
					continue;
				}
				IncludeAndroidView iav = (IncludeAndroidView) child;
				String layoutId = iav.layoutId;
				AndroidView tgt = name2View.get(layoutId);
				if (tgt != null) {
					tgt = (AndroidView) tgt.deepCopy();
					tgt.setParent(view, i);
				} else if (getLayoutFilePath(resRoot, layoutId, isSys) != null) {
					// not exist, let's get it on-demand
					String file = getLayoutFilePath(resRoot, layoutId, isSys);
					tgt = new AndroidView();
					tgt.setParent(view, i);
					tgt.setOrigin(file);
					readLayout(file, tgt, isSys);
					int newId = nonRId--;
					viewMap.put(newId, tgt);
					nameMap.put(newId, layoutId);
				} else if (sysRGeneralIdMap.get("layout").containsKey(layoutId)
						&& sysId2View.containsKey(sysRGeneralIdMap.get("layout").get(layoutId))) {
					// <include> is used with an in built android layout id
					tgt = (AndroidView) sysId2View.get(sysRGeneralIdMap.get("layout").get(layoutId)).deepCopy();
					tgt.setParent(view, i);
				} else {
					System.err.println("[WARNING] Unknown layout " + layoutId + " included by " + view.getOrigin());
					continue;
				}
				Integer includeeId = iav.includeeId;
				if (includeeId != null) {
					tgt.setId(includeeId.intValue());
				}
				work.add(tgt);
			}
		}
	}

	private void resolveIncludesApplicationProject(HashMap<Integer, String> nameMap,
			HashMap<Integer, AndroidView> viewMap) {

		HashMap<String, AndroidView> name2View = Maps.newHashMap();
		for (Map.Entry<Integer, String> entry : nameMap.entrySet()) {
			String name = entry.getValue();
			AndroidView view = viewMap.get(entry.getKey());
			name2View.put(name, view);
		}
		boolean isSys = false;
		LinkedList<AndroidView> work = Lists.newLinkedList();
		work.addAll(viewMap.values());
		while (!work.isEmpty()) {
			AndroidView view = work.remove();
			for (int i = 0; i < view.getNumberOfChildren(); i++) {
				IAndroidView child = view.getChildInternal(i);
				if (child instanceof AndroidView) {
					work.add((AndroidView) child);
					continue;
				}
				IncludeAndroidView iav = (IncludeAndroidView) child;
				String layoutId = iav.layoutId;
				AndroidView tgt = name2View.get(layoutId);
				if (tgt == null) {
					// not exist, let's get it on-demand
					String file = getLayoutFilePath(Configs.resourceLocation, layoutId, isSys);
					if (file == null) {
						System.err.println("[WARNING] Unknown layout " + layoutId + " included by " + view.getOrigin());
						continue;
					}
					tgt = new AndroidView();
					tgt.setParent(view, i);
					tgt.setOrigin(file);
					readLayout(file, tgt, isSys);
					int newId = nonRId--;
					viewMap.put(newId, tgt);
					nameMap.put(newId, layoutId);
				} else {
					tgt = (AndroidView) tgt.deepCopy();
					tgt.setParent(view, i);
				}
				Integer includeeId = iav.includeeId;
				if (includeeId != null) {
					tgt.setId(includeeId.intValue());
				}
				work.add(tgt);
			}
		}
	}

	private void readLayout(String resRoot, HashMap<Integer, String> in, HashMap<Integer, AndroidView> out,
			boolean isSys) {
		if (debug) {
			System.out.println("*** read layout of " + resRoot);
		}
		// boolean isSys = (invSysRLayoutMap == in);
		// assert Configs.project.equals(proj) ^ isSys;

		for (Map.Entry<Integer, String> entry : in.entrySet()) {
			Integer layoutFileId = entry.getKey();
			String layoutFileName = entry.getValue();
			AndroidView root = new AndroidView();
			out.put(layoutFileId, root);

			String file = getLayoutFilePath(resRoot, layoutFileName, isSys);
			if (file == null) {
				if (Configs.verbose) {
					System.err.println("[WARNING] Cannot find " + layoutFileName + ".xml in " + resRoot);
				}
				continue;
			}

			readLayout(file, root, isSys);
		}
	}

	private void readLayoutApplicationProject(HashMap<Integer, String> in, HashMap<Integer, AndroidView> out) {
		if (debug) {
			System.out.println("*** read layout of " + Configs.project);
		}

		for (Map.Entry<Integer, String> entry : in.entrySet()) {
			Integer layoutFileId = entry.getKey();
			String layoutFileName = entry.getValue();
			AndroidView root = new AndroidView();
			out.put(layoutFileId, root);

			String file = getLayoutFilePath(Configs.resourceLocation, layoutFileName, false);
			if (file == null) {
				if (Configs.verbose) {
					System.err
							.println("[WARNING] Cannot find " + layoutFileName + ".xml in " + Configs.resourceLocation);
				}
				continue;
			}

			readLayout(file, root, false);
		}
	}

	private void readLayout(String file, AndroidView root, boolean isSys) {
		Document doc;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(file);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		Element rootElement = doc.getDocumentElement();
		// In older versions, Preference could be put in layout folder and we do
		// not support Prefernce yet.
		if (rootElement.getTagName().equals("PreferenceScreen")) {
			return;
		}

		LinkedList<Pair<Node, AndroidView>> work = Lists.newLinkedList();
		work.add(new Pair<Node, AndroidView>(rootElement, root));
		while (!work.isEmpty()) {
			Pair<Node, AndroidView> p = work.removeFirst();
			Node node = p.getO1();
			AndroidView view = p.getO2();
			view.setOrigin(file);

			NamedNodeMap attrMap = node.getAttributes();
			if (attrMap == null) {
				System.out.println(
						file + "!!!" + node.getClass() + "!!!" + node.toString() + "!!!" + node.getTextContent());
			}
			// Retrieve view id (android:id)
			Node idNode = attrMap.getNamedItem(ID_ATTR);
			int guiId = -1;
			String id = null;
			if (idNode != null) {
				String txt = idNode.getTextContent();
				Pair<String, Integer> pair = parseAndroidId(txt, isSys);
				id = pair.getO1();
				Integer guiIdObj = pair.getO2();
				if (guiIdObj == null) {
					if (!isSys) {
						System.err.println("[WARNING] unresolved android:id " + id + " in " + file);
					}
				} else {
					guiId = guiIdObj.intValue();
					if (lookupNameInGeneralMap("id", guiId, isSys) == null) {
						extraId2ViewMap.put(guiIdObj, view);
					}
				}
			}

			// Retrieve view type
			String guiName = node.getNodeName();
			if ("view".equals(guiName)) {
				guiName = attrMap.getNamedItem("class").getTextContent();
			} else if (guiName.equals("MenuItemView")) {
				// FIXME(tony): this is an "approximation".
				guiName = "android.view.MenuItem";
			}

			if (debug) {
				System.out.println(guiName + " (" + guiId + ", " + id + ")");
			}

			// Retrieve callback (android:onClick)
			if (guiId != -1) {
				String callback = readAndroidCallback(attrMap, "android:onClick");
				if (callback != null) {
					Pair<String, Boolean> pair = new Pair<String, Boolean>(callback, false);
					this.callbacksXML.put(guiId, pair);
				}
			}

			// Retrieve text (android:text)
			String text = readAndroidTextOrTitle(attrMap, TEXT_ATTR);

			view.save(guiId, text, guiName);

			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node newNode = children.item(i);
				String nodeName = newNode.getNodeName();
				if ("#comment".equals(nodeName)) {
					continue;
				}
				if ("#text".equals(nodeName)) {
					// possible for XML files created on a different operating
					// system
					// than the one our analysis is run on
					continue;
				}
				if (nodeName.equals("requestFocus")) {
					continue;
				}
				if (!newNode.hasAttributes() && !"TableRow".equals(nodeName) && !"View".equals(nodeName)) {
					System.err.println("[WARNING] no attribute node " + newNode.getNodeName());
				}

				if (newNode.getNodeName().equals("include")) {
					attrMap = newNode.getAttributes();
					String layoutTxt = attrMap.getNamedItem("layout").getTextContent();
					String layoutId = null;
					if (layoutTxt.startsWith("@layout/")) {
						layoutId = layoutTxt.substring("@layout/".length());
					} else if (layoutTxt.startsWith("@android:layout/")) {
						layoutId = layoutTxt.substring("@android:layout/".length());
					} else if (layoutTxt.matches("@\\*android:layout\\/(\\w)+")) {
						layoutId = layoutTxt.substring("@*android:layout/".length());
					} else {
						throw new RuntimeException("[WARNING] Unhandled layout id " + layoutTxt);
					}
					Integer includeeId = null;
					id = null;
					idNode = attrMap.getNamedItem(ID_ATTR);
					if (idNode != null) {
						String txt = idNode.getTextContent();
						Pair<String, Integer> pair = parseAndroidId(txt, isSys);
						id = pair.getO1();
						Integer guiIdObj = pair.getO2();
						if (guiIdObj == null) {
							if (!isSys) {
								System.err.println("[WARNING] unresolved android:id " + id + " in " + file);
							}
						} else {
							includeeId = guiIdObj;
						}
					}

					// view.saveInclude(layoutId, includeeId);
					IncludeAndroidView iav = new IncludeAndroidView(layoutId, includeeId);
					iav.setParent(view);
				} else {
					AndroidView newView = new AndroidView();
					newView.setParent(view);
					work.add(new Pair<Node, AndroidView>(newNode, newView));
				}
			}
		}
	}

	private static String getLayoutFilePath(String resRoot, String layoutId, boolean isSys) {
		// special cases
		if ("keyguard_eca".equals(layoutId)) {
			// its real name is defined in values*/alias.xml
			// for our purpose, we can simply hack it
			assert isSys;
			// use the value for portrait
			String ret = resRoot + "/layout/keyguard_emergency_carrier_area.xml";
			assert new File(ret).exists() : "ret=" + ret;
			return ret;
		}
		if ("status_bar_latest_event_ticker_large_icon".equals(layoutId)
				|| "status_bar_latest_event_ticker".equals(layoutId) || "keyguard_screen_status_land".equals(layoutId)
				|| "keyguard_screen_status_port".equals(layoutId)) {
			assert isSys;
			String ret = findFileExistence(resRoot, "layout", layoutId + ".xml");

			assert new File(ret).exists() : "ret=" + ret;
			return ret;
		}
		ArrayList<String> projectDirs = Lists.newArrayList();
		projectDirs.add(resRoot);
		if (!isSys) {
			for (String s : Configs.resourceLocationList) {
				if (!projectDirs.contains(s)) {
					projectDirs.add(s);
				}
			}
		}

		for (String proj : projectDirs) {
			String file = findFileExistence(proj, "layout", layoutId + ".xml");
			if (file == null) {
				continue;
			}
			if (new File(file).exists()) {
				return file;
			}
		}
		return null;
	}

	private String readAndroidCallback(NamedNodeMap attrMap, String callback) {
		Node node = attrMap.getNamedItem(callback);
		if (node == null) {
			return null;
		}
		String refOrValue = node.getTextContent();
		if (debug) {
			System.out.println("  * `" + refOrValue + "' -> `" + refOrValue + "'");
		}
		return refOrValue;
	}

	private Integer lookupIdInGeneralMap(String type, String name, boolean isSys) {
		assert type != null;
		assert name != null;
		HashMap<String, HashMap<String, Integer>> workingMap;
		if (isSys) {
			workingMap = sysRGeneralIdMap;
		} else {
			workingMap = rGeneralIdMap;
		}

		assert workingMap != null;
		HashMap<String, Integer> workingIdMap = workingMap.get(type);
		if (workingIdMap == null) {
			if (Configs.verbose) {
				Logger.verb("XMLID", "id type: " + type + " does not exist sys:" + isSys);
			}
			return null;
		}
		Integer rtnVal = workingIdMap.get(name);

		if (rtnVal == null) {
			if (Configs.verbose) {
				Logger.verb("XMLID", "id " + name + " in type " + type + " does not exist sys:" + isSys);
			}
		}
		return rtnVal;
	}

	private String lookupNameInGeneralMap(String type, Integer val, boolean isSys) {
		assert type != null;
		assert val != null;
		HashMap<String, HashMap<Integer, String>> workingMap;
		if (isSys) {
			workingMap = invSysRGeneralIdMap;
		} else {
			workingMap = invRGeneralIdMap;
		}

		assert workingMap != null;
		HashMap<Integer, String> workingIdMap = workingMap.get(type);
		if (workingIdMap == null) {
			if (Configs.verbose) {
				Logger.verb("XMLID", "id type: " + type + " does not exist");
			}
			return null;
		}
		String rtnVal = workingIdMap.get(val);

		if (rtnVal == null) {
			if (Configs.verbose) {
				Logger.verb("XMLID", "val " + val + " in type " + type + " does not exist");
			}
		}
		return rtnVal;
	}

	private String lookupNameInGeneralMap(Integer val, boolean isSys) {
		assert val != null;
		HashMap<String, HashMap<Integer, String>> workingMap;
		if (isSys) {
			workingMap = invSysRGeneralIdMap;
		} else {
			workingMap = invRGeneralIdMap;
		}
		assert workingMap != null;
		for (String type : workingMap.keySet()) {
			HashMap<Integer, String> workingIdMap = workingMap.get(type);
			if (workingIdMap.containsKey(val)) {
				String name = workingIdMap.get(val);
				if (!type.equals("id")) {
					return type + "_" + name;
				} else {
					return name;
				}
			}
		}
		return null;
	}

	private Pair<String, Integer> parseAndroidId(String txt, boolean isSys) {
		String id = null;
		Integer guiIdObj = null;
		if ("@+android:id/internalEmpty".equals(txt)) {
			id = "internalEmpty";
			guiIdObj = lookupIdInGeneralMap("id", id, true);
		} else if (txt.startsWith("@id/android:")) {
			id = txt.substring(12);
			guiIdObj = lookupIdInGeneralMap("id", id, true);
		} else if (txt.startsWith("@+id/android:") || txt.startsWith("@+android:id/")) { // handle
																							// old
																							// code
			id = txt.substring(13);
			guiIdObj = lookupIdInGeneralMap("id", id, true);
		} else if (txt.startsWith("@+id")) {
			id = txt.substring(5);
			guiIdObj = lookupIdInGeneralMap("id", id, isSys);
		} else if (txt.startsWith("@id/")) {
			id = txt.substring(4);
			guiIdObj = lookupIdInGeneralMap("id", id, isSys);
		} else if (txt.startsWith("@android:id")) {
			id = txt.substring(12);
			// guiIdObj = sysRIdMap.get(id);
			guiIdObj = lookupIdInGeneralMap("id", id, true);
		} else if (txt.startsWith("@android:attr/")) {
			id = txt.substring(14);
			// guiIdObj = sysRIdMap.get(id);
			guiIdObj = lookupIdInGeneralMap("attr", id, true);
		} else if (txt.matches("@\\*android:(\\w)+\\/(\\w)+")) {
			int idxOfColon = txt.indexOf(":");
			int idxOfSlash = txt.indexOf("/");
			String type = txt.substring(idxOfColon + 1, idxOfSlash);
			id = txt.substring(idxOfSlash + 1);
			guiIdObj = lookupIdInGeneralMap(type, id, true);
		} else if (txt.matches("@android:(\\w)+\\/(\\w)+")) {
			int idxOfColon = txt.indexOf(":");
			int idxOfSlash = txt.indexOf("/");
			String type = txt.substring(idxOfColon + 1, idxOfSlash);
			id = txt.substring(idxOfSlash + 1);
			guiIdObj = lookupIdInGeneralMap(type, id, true);
		} else if (txt.matches("@(\\w)+\\/(\\w)+")) {
			int idxOfSlash = txt.indexOf("/");
			String type = txt.substring(1, idxOfSlash);
			id = txt.substring(idxOfSlash + 1);
			guiIdObj = lookupIdInGeneralMap(type, id, isSys);
			// Logger.verb("parseAndroidId", "general match at " + txt);
		} else {
			throw new RuntimeException("[WARNING] Unhandled android:id prefix " + txt);
		}
		return new Pair<String, Integer>(id, guiIdObj);
	}

	// --- END

	// --- read menu*/*.xml
	private void readMenu() {
		readMenu(Configs.resourceLocation + "/", invRGeneralIdMap.get("menu"), id2View, false);
		// readMenuApplicationProject(invRMenuMap, id2View);
		readMenu(Configs.sysProj + "/res/", invSysRGeneralIdMap.get("menu"), sysId2View, true);
	}

	private void readMenu(String resRoot, HashMap<Integer, String> map, HashMap<Integer, AndroidView> viewMap,
			boolean isSys) {
		// boolean isSys = (map == invSysRMenuMap);
		// assert proj.equals(Configs.project) ^ isSys;

		for (Map.Entry<Integer, String> e : map.entrySet()) {
			Integer val = e.getKey();
			String name = e.getValue();
			AndroidView root = new AndroidView();
			viewMap.put(val, root);
			String file = getMenuFilePath(resRoot, name, isSys);
			if (file == null) {
				if (Configs.verbose) {
					System.err.println("Unknown menu " + name + " for " + resRoot);
				}
				continue;
			}
			root.setOrigin(file);
			if (debug) {
				System.out.println("--- reading " + file);
			}

			readMenu(file, root, isSys);
		}
	}

	private void readMenuApplicationProject(HashMap<Integer, String> map, HashMap<Integer, AndroidView> viewMap) {
		boolean isSys = false;

		for (Map.Entry<Integer, String> e : map.entrySet()) {
			Integer val = e.getKey();
			String name = e.getValue();
			AndroidView root = new AndroidView();
			viewMap.put(val, root);
			String file = getMenuFilePath(Configs.resourceLocation, name, isSys);
			if (file == null) {
				System.err.println("Unknown menu " + name + " for " + Configs.project);
				continue;
			}
			root.setOrigin(file);
			if (debug) {
				System.out.println("--- reading " + file);
			}

			readMenu(file, root, isSys);
		}
	}

	private void readMenu(String file, AndroidView root, boolean isSys) {
		Document doc;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(file);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		LinkedList<Pair<Node, AndroidView>> worklist = Lists.newLinkedList();
		worklist.add(new Pair<Node, AndroidView>(doc.getDocumentElement(), root));
		root = null;
		while (!worklist.isEmpty()) {
			Pair<Node, AndroidView> pair = worklist.remove();
			Node node = pair.getO1();
			AndroidView view = pair.getO2();
			NamedNodeMap attrMap = node.getAttributes();
			Node idNode = attrMap.getNamedItem(ID_ATTR);
			int guiId = -1;
			String id = null;
			if (idNode != null) {
				String txt = idNode.getTextContent();
				Pair<String, Integer> p = parseAndroidId(txt, isSys);
				id = p.getO1();
				Integer guiIdObj = p.getO2();
				if (guiIdObj == null) {
					if (!isSys) {
						System.err.println("[WARNING] unresolved android:id " + id + " in " + file);
					}
					guiId = nonRId--; // negative value to indicate it is a
										// unique id but
										// we don't know its value
					feedIdIntoGeneralMap("id", id, guiId, isSys);
					// if (isSys) {
					// sysRIdMap.put(id, guiId);
					// invSysRIdMap.put(guiId, id);
					// } else {
					// rIdMap.put(id, guiId);
					// invRIdMap.put(guiId, id);
					// }
				} else {
					guiId = guiIdObj.intValue();
				}
			}

			// FIXME(tony): this is an "approximation"
			String guiName = node.getNodeName();
			if (guiName.equals("menu")) {
				guiName = "android.view.Menu";
			} else if (guiName.equals("item")) {
				guiName = "android.view.MenuItem";
			} else if (guiName.equals("group")) {
				// TODO(tony): we might want to create a special fake class to
				// represent menu groups. But for now, let's simply pretend it's
				// a ViewGroup. Also, print a warning when we do see <group>
				if (Configs.verbose) {
					System.out.println("[TODO] <group> used in " + file);
				}
				guiName = "android.view.ViewGroup";
			} else {
				if (Configs.verbose) {
					Logger.verb("XML", "Unhandled menu tag " + guiName);
				}
				// throw new RuntimeException("Unhandled menu tag " + guiName);
			}
			if (debug) {
				System.out.println(guiName + " (" + guiId + ", " + id + ")");
			}
			String text = readAndroidTextOrTitle(attrMap, TITLE_ATTR);

			view.save(guiId, text, guiName);
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node newNode = children.item(i);
				String nodeName = newNode.getNodeName();
				if ("#comment".equals(nodeName)) {
					continue;
				}
				if ("#text".equals(nodeName)) {
					// possible for XML files created on a different operating
					// system
					// than the one our analysis is run on
					continue;
				}

				AndroidView newView = new AndroidView();
				// FIXME: we assume that every node has attributes, may be wrong
				if (!newNode.hasAttributes()) {
					Logger.verb("WARNING", "xml node " + newNode + " has no attributes");
					continue;
				} else {
					NamedNodeMap attrs = newNode.getAttributes();
					for (int idx = 0; idx < attrs.getLength(); idx += 1) {
						Node attr = attrs.item(idx);
						String name = attr.getNodeName();
						String value = attr.getNodeValue();
						newView.addAttr(name, value);
					}
				}
				newView.setParent(view);
				worklist.add(new Pair<Node, AndroidView>(newNode, newView));
			}
		}
	}

	private String getMenuFilePath(String project, String menuId, boolean isSys) {
		ArrayList<String> projectDirs = Lists.newArrayList();
		// projectDirs.add(project);
		if (!isSys) {
			projectDirs.addAll(Configs.resourceLocationList);
		}

		for (String proj : projectDirs) {
			String file = findFileExistence(proj, "menu", menuId + ".xml");
			/*
			 * String file = proj + "/res/menu/" + menuId + ".xml"; if (!new
			 * File(file).exists()) { file = null; }
			 */
			if (file != null) {
				return file;
			}
		}
		return null;
	}
	// --- END

	// --- read values/*.xml
	private void readStrings() {
		intAndStringValues = Maps.newHashMap();
		rStringAndStringValues = Maps.newHashMap();
		for (String file : getStringXMLFilePaths(Configs.resourceLocation, false)) {
			readStrings(file, intAndStringValues, rStringAndStringValues, rGeneralIdMap.get("string"));
		}

		for (String file : getStringXMLFilePaths(Configs.sysProj + "/res", true)) {
			readStrings(file, sysIntAndStringValues, sysRStringAndStringValues, sysRGeneralIdMap.get("string"));
		}
	}

	final static String SYS_ANDROID_STRING_REF = "@android:string/";
	final static int SYS_ANDROID_STRING_REF_LENGTH = SYS_ANDROID_STRING_REF.length();

	final static String ANOTHER_SYS_ANDROID_STRING_REF = "@*android:string/";
	final static int ANOTHER_SYS_ANDROID_STRING_REF_LENGTH = ANOTHER_SYS_ANDROID_STRING_REF.length();

	final static String MOSTLY_APP_ANDROID_STRING_REF = "@string/";
	final static int MOSTLY_APP_ANDROID_STRING_REF_LENGTH = MOSTLY_APP_ANDROID_STRING_REF.length();

	String convertAndroidTextToString(String androidText) {
		if (androidText.isEmpty()) {
			return null;
		}
		// Is it string ref
		if (androidText.charAt(0) == '@') {
			if (androidText.startsWith(SYS_ANDROID_STRING_REF)) {
				return sysRStringAndStringValues.get(androidText.substring(SYS_ANDROID_STRING_REF_LENGTH));
			}
			if (androidText.startsWith(ANOTHER_SYS_ANDROID_STRING_REF)) {
				return sysRStringAndStringValues.get(androidText.substring(ANOTHER_SYS_ANDROID_STRING_REF_LENGTH));
			}
			if (androidText.startsWith(MOSTLY_APP_ANDROID_STRING_REF)) {
				String stringName = androidText.substring(MOSTLY_APP_ANDROID_STRING_REF_LENGTH);
				String result = rStringAndStringValues.get(stringName);
				if (result == null) {
					result = sysRStringAndStringValues.get(stringName);
				}
				return result;
			}
			// Workaround for a weird case in XBMC
			return null;
			// throw new RuntimeException("Unknown android:text format " +
			// androidText);
		} else {
			return androidText;
		}
	}

	String readAndroidTextOrTitle(NamedNodeMap attrMap, String attributeName) {
		Node textNode = attrMap.getNamedItem(attributeName);
		String text = null;
		if (textNode != null) {
			String refOrValue = textNode.getTextContent();
			text = convertAndroidTextToString(refOrValue);
			if (debug) {
				System.out.println("  * `" + refOrValue + "' -> `" + text + "'");
			}
		}
		return text;
	}

	private void readStrings(String file, HashMap<Integer, String> idAndStrings,
			HashMap<String, String> stringFieldAndStrings, HashMap<String, Integer> stringFieldAndIds) {
		Document doc;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(file);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		if (Configs.verbose) {
			System.out.println("--- Reading " + file);
		}
		NodeList nodes = doc.getElementsByTagName("string");
		if (nodes == null) {
			return;
		}
		for (int i = 0; i < nodes.getLength(); i++) {
			Node n = nodes.item(i);
			if (!"string".equals(n.getNodeName())) {
				throw new RuntimeException();
			}
			NamedNodeMap attrs = n.getAttributes();
			String stringName = attrs.getNamedItem("name").getTextContent();
			NodeList childNodes = n.getChildNodes();
			String stringValue;
			if (childNodes.getLength() == 0) {
				stringValue = "";
			} else {
				stringValue = eliminateQuotes(childNodes.item(0).getTextContent());
			}
			stringFieldAndStrings.put(stringName, stringValue);

			Integer idValueObj = stringFieldAndIds.get(stringName);
			if (idValueObj == null) {
				if (debug) {
					throw new RuntimeException("Unknown string node " + stringName + " defined in " + file);
				}
			} else {
				idAndStrings.put(idValueObj, stringValue);
			}
		}
	}

	private String eliminateQuotes(String s) {
		int len = s.length();
		if (len > 1 && s.charAt(0) == '"' && s.charAt(len - 1) == '"') {
			return s.substring(1, len - 1);
		}
		return s;
	}

	/*
	 * Usually the file name is strings.xml, but it technically can be anything.
	 * For now, let's read strings.xml and strings-*.xml.
	 */
	private ArrayList<String> getStringXMLFilePaths(String resRoot, boolean isSys) {
		ArrayList<String> projectDirs = Lists.newArrayList();
		projectDirs.add(resRoot);

		if (!isSys) {
			for (String s : Configs.resourceLocationList) {
				if (!projectDirs.contains(s)) {
					projectDirs.add(s);
				}
			}
		}
		ArrayList<String> xmlFiles = Lists.newArrayList();
		for (String proj : projectDirs) {
			String valuesDirectoryName = proj + "/values/";
			File valuesDirectory = new File(valuesDirectoryName);
			if (!valuesDirectory.exists()) {
				System.out.println("[WARNING] Directory " + valuesDirectory + " does not exist!");
				return Lists.newArrayList();
			}
			for (String file : valuesDirectory.list()) {
				if (file.equals("strings.xml") || (file.startsWith("strings-") && file.endsWith(".xml"))) {
					xmlFiles.add(valuesDirectoryName + file);
				}
			}
		}
		return xmlFiles;
	}
	// --- END

	// === END

	private static String findFileExistence(String folderName, String dirName, String tgtFileName) {
		File folder = new File(folderName);
		for (File subFolder : folder.listFiles()) {
			if (subFolder.isDirectory()) {
				String subDirName = subFolder.getName();
				if (subDirName.length() < dirName.length()) {
					continue;
				}
				if (subDirName.startsWith(dirName)) {
					for (File subFile : subFolder.listFiles()) {
						if (subFile.getName().equals(tgtFileName))
							return folderName + "/" + subDirName + "/" + tgtFileName;
					}
				}
			}
		}
		return null;
	}

	// record callbacks defined in xml
	private HashMap<Integer, Pair<String, Boolean>> callbacksXML = new HashMap<Integer, Pair<String, Boolean>>();

	@Override
	public Map<Integer, Pair<String, Boolean>> retrieveCallbacks() {
		return callbacksXML;
	}

	private void printMap(Map theMap, String name) {
		int iSize = 0;
		if (theMap != null) {
			iSize = theMap.size();
		}
		System.out.println("[DEBUGXML] Map " + name + "size: " + iSize);
		if (iSize == 0) {
			return;
		}
		Set<Object> keys = theMap.keySet();
		for (Object key : keys) {
			Object value = theMap.get(key);
			System.out.println(String.format("KEY Type: %s, %s VALUE Type: %s, %s", key.getClass().getSimpleName(),
					key.toString(), value.getClass().getSimpleName(), value.toString()));

		}
	}

	public void debugPrintAll() {

		System.out.println("Activities:");
		for (String str : this.activities) {
			System.out.println(str);
		}
		System.out.println("Services:");
		for (String str : this.services) {
			System.out.println(str);
		}
	}

	@Override
	public Set<Integer> getDrawableIdValues() {
		return invRGeneralIdMap.get("drawable").keySet();
	}
}
