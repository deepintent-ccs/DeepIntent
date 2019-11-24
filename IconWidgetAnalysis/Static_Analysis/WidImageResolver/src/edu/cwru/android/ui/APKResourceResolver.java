package edu.cwru.android.ui;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import com.android.utils.Pair;

import android.util.TypedValue;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResType;
import brut.androlib.res.data.ResTypeSpec;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.data.value.ResValue;
import brut.androlib.res.decoder.AXmlResourceParser;
import edu.cwru.android.ui.config.UIRenderingConfig;

public class APKResourceResolver {

	private static class SensitiveInputField {
		private final static int TYPE_NUMBER_VARIATION_PASSWORD = 0x00000012;
		private final static int TYPE_TEXT_VARIATION_PASSWORD = 0x00000081;
		private final static int TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000091;
		private final static int TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e1;
		private final static int TYPE_TEXT_VARIATION_FILTER = 0x000000b1;

		/**
		 * Validate if a given InputType indicate a sensitive field
		 *
		 * @param inputType
		 * @param defaultValue
		 * @return TRUE if inputType or defaultValue indicates this field is
		 *         password related input box; otherwise FALSE.
		 */
		static boolean validate(int inputType, boolean defaultValue) {
			if (0 == inputType) {
				return defaultValue;
			} else {
				if ((inputType & TYPE_TEXT_VARIATION_FILTER) == TYPE_TEXT_VARIATION_FILTER)
					inputType = inputType - TYPE_TEXT_VARIATION_FILTER + 1;// keep
																			// TYPE_TEXT_TEXT
				if ((inputType & TYPE_NUMBER_VARIATION_PASSWORD) == TYPE_NUMBER_VARIATION_PASSWORD
						|| (inputType & TYPE_TEXT_VARIATION_PASSWORD) == TYPE_TEXT_VARIATION_PASSWORD
						|| (inputType & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
						|| (inputType & TYPE_TEXT_VARIATION_WEB_PASSWORD) == TYPE_TEXT_VARIATION_WEB_PASSWORD)
					return true;
			}
			return defaultValue;
		}
	}

	private Map<String, Map<String, Integer>> supportName2id;

	public APKResourceResolver(String apkfile) {
		this.apkfilename = apkfile;
		try {
			this.supportName2id = new RResParser().parseNameToId("appres/R.txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private static org.apache.logging.log4j.Logger log = LogManager.getFormatterLogger(APKResourceResolver.class);

	private String apkfilename = null;
	private ZipFile apkAchive;
	private ResTable resTable;
	private HashMap<Integer, String> id2Name;
	private HashMap<String, Integer> name2Id;
	private Map<String, Set<String>> included2XMLs;// <include> tag to enclosing
													// XMLs
	private Map<Integer, List<ResLayout>> id2Node;// for layout xml
	private Map<String, List<ResLayout>> file2Nodes;// for layout xml: file ->
													// node list
	private String appPackage;
	private int appThemeId;
	private Set<Integer> sensitiveFields;// sensitive input field
	private Set<String> editableXMLs;// XMLs containing editable field
	private Set<Integer> editableFields;// all input fields (EditText &
										// sub-classes)
	private boolean[] specifiedAPILevels;
	private short currentAPILevelIdx = 0;
	private BufferedWriter labelWriter;
	private final int LowestAPILevel = 18;// the lowest API level supported in
											// rendering
	private Set<String> goodLayoutFiles;// contains GOOD files defined in
										// resources.arsc (some XMLs in
										// res/layout are
										// not in resources.arsc)
	private List<Pair<String, Boolean>> usedThemes;// collection of used
													// Themes/isProjTheme in
													// AndroidManifest.xml
	private int nextAvailableThemeIdx = 0;
	// default language is NULL string. use "##" to label it.
	public final static String DefaultLanguage = "##";
	
	public String getPackageName(){
		return appPackage;
	}

	private void extractManifest(ZipEntry manifestEntry) {
		Map<Integer, Integer> themeId2UsedTimes = new HashMap<Integer, Integer>();
		try {
			log.info("Try parsing AndroidManifest for '%s'...", apkfilename);
			InputStream xmlIS = apkAchive.getInputStream(manifestEntry);
			AXmlResourceParser parser = new AXmlResourceParser();
			parser.open(xmlIS);
			// parser.open(xmlIS);
			int type = -1;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if (type == XmlPullParser.START_TAG) {
					String tagName = parser.getName();
					if (tagName.equals("manifest")) {
						appPackage = getAttributeTextValue(parser, "package");
					} else if (tagName.equals("application")) {
						appThemeId = getAttributeIdValue(parser, "theme");
						// break;
					} else if (tagName.equals("activity")) {
						int themeId = getAttributeIdValue(parser, "theme");
						if (!validId(themeId))
							continue;
						Integer usedTimes = themeId2UsedTimes.get(themeId);
						int times = 1;
						if (usedTimes != null) {
							times += usedTimes.intValue();
						}
						themeId2UsedTimes.put(themeId, times);
					}
				}
			}
			parser.close();
		} catch (Exception e) {
			log.error("Error handling XML '%s'");
		}
		postExtractManifest(themeId2UsedTimes);
	}

	/**
	 * Retrieve the suitable *NEXT* API level to render for failing layout XMLs.
	 * If no API levels specified in style configuration, simple use the
	 * MaxSupportedAPILevel; otherwise, use the one closest to the first
	 * specified level. For example, there are 3 folders values/, values-v13/
	 * and values-v15/. The first try would be API level 12; the second would be
	 * 14 and the last would be MaxSupportedAPILevel. If MaxSupportedAPILevel is
	 * 17 and there is a folder values-v17/ then 17 would be the last try. If no
	 * more API levels to explore, return value is -1. Use this to check if we
	 * need more tries. We assume that nowadays, apps will support at least API
	 * level 10.
	 *
	 * @return the most suitable *NEXT* specified API level or -1 for error (no
	 *         more API levels to explore)
	 */
	public int getNextSpecifiedAPILevel() {
		nextAvailableThemeIdx = 0;// always reset the available Theme Idx to
									// pick from the beginning
		if (specifiedAPILevels == null)
			if (currentAPILevelIdx++ == 0)
				return UIRenderingConfig.MaxSupportedAPILevel;
			else
				return -1;
		int apiLevel = -1;
		while (currentAPILevelIdx < specifiedAPILevels.length) {
			if (specifiedAPILevels[currentAPILevelIdx]) {
				apiLevel = LowestAPILevel + currentAPILevelIdx;
				currentAPILevelIdx++;
				break;
			}
			currentAPILevelIdx++;
		}
		if (currentAPILevelIdx == specifiedAPILevels.length) {
			apiLevel = LowestAPILevel + currentAPILevelIdx;
			currentAPILevelIdx++;
		} else if (currentAPILevelIdx > specifiedAPILevels.length) {
			apiLevel = -1;
		}
		return apiLevel;
	}

	/**
	 * Get all layout XML files' name. These layout XMLs should be in folder
	 * res/layout and the names are all full name like 'res/layout/main.xml'.
	 *
	 * @return empty set or all the names.
	 */
	public Set<String> allLayoutXMLs() {
		if (file2Nodes != null)
			return file2Nodes.keySet();
		return Collections.emptySet();
	}

	public Set<String> getEditableXMLs() {
		return editableXMLs;
	}

	private static class HackingAndroidR {
		static Map<Integer, String> id2StyleName = null;

		private static void hack() {
			id2StyleName = new HashMap<Integer, String>();
			try {
				Class<?> klass = Class.forName("android.R$style");
				Field[] allFields = klass.getFields();
				for (int i = 0; i < allFields.length; i++) {
					Field f = allFields[i];
					String name = f.getName().replace('_', '.');
					int value = f.getInt(name);
					// System.out.format ("%s -- %08x\n", name , value);
					id2StyleName.put(Integer.valueOf(value), name);
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		static String getStyle(int id) {
			if (id2StyleName == null) {
				hack();
			}
			return id2StyleName.get(Integer.valueOf(id));
		}
	}

	/**
	 * Get Theme name (e.g. Theme.AppTheme) by ID. If the ID indicates a
	 * systematic resource, a reflection to android.R$style is applied.
	 *
	 * @param themeId
	 * @return A Pair of theme with the String name and boolean indicator for
	 *         project or system resource.
	 */
	private Pair<String, Boolean> getTheme(int themeId) {
		String theme = resolveNameForId(themeId);
		if (theme != null) {
			// Misc.ASSERT(theme.startsWith("style/"),
			// "An invalid theme name '%s' %08x is retrieved. Check the resource
			// parsing process.", theme, themeId);
			// in 'AM-com.baidu.browser.apps-23.apk', some Activity has
			// android:theme="@color/black"
			if (theme.startsWith("style/")) {
				int lastSlash = theme.lastIndexOf('/');
				theme = theme.substring(lastSlash + 1);
			} else {
				theme = null;
			}
			return Pair.of(theme, Boolean.TRUE);
		} else {
			theme = HackingAndroidR.getStyle(themeId);
			return Pair.of(theme, Boolean.FALSE);
		}
	}

	private void postExtractManifest(Map<Integer, Integer> themeId2UsedTimes) {
		int laterInsertPos = 0;
		usedThemes = new LinkedList<Pair<String, Boolean>>();
		if (0 != appThemeId) {
			Pair<String, Boolean> appTheme = getTheme(appThemeId);
			if (null != appTheme.getFirst()) {
				usedThemes.add(appTheme);
				laterInsertPos = 1;
			}
		}
		Set<ThemeIdPair> sortedThemeId2UsedTimes = new TreeSet<ThemeIdPair>();
		Set<Map.Entry<Integer, Integer>> entrySet = themeId2UsedTimes.entrySet();
		for (Map.Entry<Integer, Integer> entry : entrySet) {
			int themeId = entry.getKey().intValue();
			int usedTimes = entry.getValue().intValue();
			sortedThemeId2UsedTimes.add(new ThemeIdPair(themeId, usedTimes));
		}
		for (ThemeIdPair tip : sortedThemeId2UsedTimes) {
			int themeId = tip.ThemeId;
			if (themeId == appThemeId)
				continue;
			Pair<String, Boolean> theme = getTheme(themeId);
			if (theme.getFirst() != null) {
				usedThemes.add(laterInsertPos, theme);
			}
		}
		// use "Theme.Light" instead of "Theme" because under "Theme" the bg is
		// dark and some text cannot be seen on the
		// output image. But under "Theme.Light", the bg is white and some texts
		// in some apps are also white.
		// using the app defined theme may not solve the problem. e.g. chase has
		// blue bg on a phone but rendered image
		// has a dark bg, which makes some text disappear.
		if (usedThemes.isEmpty()) {
			usedThemes.add(Pair.of("Theme.Light", Boolean.FALSE));
		}
	}

	public void extractARSCAndLayoutFiles() {

		File apkF = new File(apkfilename);
		if (!apkF.exists() || !apkF.canRead()) {
			System.out.println("Error Reading File " + apkfilename);
			return;
		}
		ZipEntry arscEntry = null, manifestEntry = null;
		Map<String, ZipEntry> layoutEntries = new HashMap<String, ZipEntry>();
		Map<String, ZipEntry> drawableEntries = new HashMap<String, ZipEntry>();

		try {
			apkAchive = new ZipFile(apkF);

			Enumeration<?> enums = apkAchive.entries();
			while (enums.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) enums.nextElement();
				String entryName = entry.getName();
				log.info("+ENTRY: " + entryName);
				if ("AndroidManifest.xml".equals(entryName)) {
					manifestEntry = entry;
				} else if ("resources.arsc".equals(entryName)) {
					arscEntry = entry;
				} else if (entryName.startsWith("res/layout/") && entryName.endsWith(".xml")) {
					layoutEntries.put(entryName, entry);
				} else if (entryName.startsWith("res/drawable/") && entryName.endsWith(".xml")) {
					drawableEntries.put(entryName, entry);
				}
			}
			// Manifest and Layout need resource mapping
			extractResourceARSC();
			extractManifest(manifestEntry);
			extractLayoutXML(layoutEntries);
			extractImageResourceXML(drawableEntries);
		} catch (Exception e) {
			log.error("Error Handling File %s: %s", apkfilename, e);
		} finally {
			try {
				apkAchive.close();
			} catch (IOException e) {
				// skip
			}
		}
		layoutEntries = null;
	}

	private void extractImageResourceXML(Map<String, ZipEntry> drawableEntries) throws Exception {
		String outputfile = "";
		String[] filesplit = null;
		if (apkfilename.contains("/")) {
			filesplit = apkfilename.split("/");

		} else if (apkfilename.contains("\\")) {
			filesplit = apkfilename.split("\\\\");
		}
		if (filesplit!=null) {
			outputfile = filesplit[filesplit.length - 1].substring(0, filesplit[filesplit.length - 1].length() - 4);
		}else{
			outputfile = apkfilename.substring(0, apkfilename.length()-4);
		}
		
		PrintWriter out = new PrintWriter("output/" + outputfile + ".image.json");
		HashMap<String, Set<String>> map = new HashMap<>();
		Set<Map.Entry<String, ZipEntry>> entrySet = drawableEntries.entrySet();
		for (Map.Entry<String, ZipEntry> entry : entrySet) {
			String name = entry.getKey();
			ZipEntry xml = entry.getValue();
			String[] split = name.split("/");
			String filename = split[split.length - 1].substring(0, split[split.length - 1].length() - 4);

			log.info("handling %s,%s", name, xml);
			try {
				InputStream xmlIS = apkAchive.getInputStream(xml);
				AXmlResourceParser parser = new AXmlResourceParser();
				parser.open(xmlIS);
				int type = -1;
				while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
					switch (type) {
					case XmlPullParser.START_DOCUMENT:
						break;
					case XmlPullParser.START_TAG:
						String tagName = parser.getName();
						log.info("handling " + tagName + " in " + xml);
						int tagId = getAttributeIdValue(parser, "id");
						String imagenames = getAttributeTextValue(parser, "drawable");
						String resourceRefName = "";
						for (int i = 0; i < parser.getAttributeCount(); i++) {
							String attrName = parser.getAttributeName(i);
							if (attrName.equals("drawable")) {
								int valueType = parser.getAttributeValueType(i);
								String attributeValue = parser.getAttributeValue(i);

								if (valueType == TypedValue.TYPE_REFERENCE) {
									if (attributeValue.startsWith("@")) {
										int resId = Integer.parseInt(attributeValue.substring(1));
										resourceRefName = id2Name.get(resId);
									}

								} else if (valueType == TypedValue.TYPE_STRING) {
									resourceRefName = attributeValue;
								} else {
									log.debug("Unexpected value type for <%s %s=[TYPE:0x%08x] .../>", parser.getName(),
											attrName, valueType);
								}
								break;
							}

						}
						if (!resourceRefName.equals("")) {
							if (!map.containsKey(filename)) {
								map.put(filename, new HashSet<>());
							}
							map.get(filename).add(resourceRefName.substring(9));
						}
						break;
					case XmlPullParser.END_TAG:
						break;
					case XmlPullParser.END_DOCUMENT:
						break;
					}
				}
				parser.close();

			} catch (Throwable e) {
				log.error("Error handling XML '" + name + "'", e);
			}
		}

		JSONArray array = new JSONArray();
		for (Entry<String, Set<String>> entry : map.entrySet()) {
			JSONObject obj = new JSONObject();
			obj.put("name", entry.getKey());
			JSONArray images = new JSONArray();
			obj.put("images", images);
			for (String imagename : entry.getValue()) {
				images.add(imagename);
			}
			array.add(obj);
		}
		out.println(array.toJSONString());
		out.close();
		// TODO Auto-generated method stub

	}

	private void extractResourceARSC() {
		try {
			log.info("Try building ID<-->NAME mapping for '%s'...", apkfilename);
			// InputStream arscIS = apkAchive.getInputStream(arscEntry);
			File frameworkfile = new File("/Users/xushengxiao/Library/apktool/framework/1.apk");
			resTable = new ResLoader().getResTable(new File(apkfilename));
			// DumpARSC(resTable);
			buildResIdMap();
		} catch (Exception e) {
			log.error("Error building ID/NAME mapping for resources.arsc: %s.", e);
		}
	}

	private void addAPILevelInStyle(short apiLevel) throws Exception {
		// low versions are ignored. If only '0' is as the version (in folder
		// values/), it is also ignored.
		if (apiLevel <= LowestAPILevel)
			return;
		if (specifiedAPILevels == null) {
			if (UIRenderingConfig.MaxSupportedAPILevel < LowestAPILevel) {
				log.error("MaxSupportedAPILevel in config should be beyond '%d'", LowestAPILevel);
				throw new Exception(
						String.format("MaxSupportedAPILevel in config should be beyond '%d'", LowestAPILevel));
			}

			// location ZERO is used to hold default level 'LowestAPILevel'
			specifiedAPILevels = new boolean[UIRenderingConfig.MaxSupportedAPILevel - LowestAPILevel];
		}
		if (apiLevel > UIRenderingConfig.MaxSupportedAPILevel) {
			log.info("API level '%d' larger than configured MaxSupportedAPILevel '%d' is IGNORED.", apiLevel,
					UIRenderingConfig.MaxSupportedAPILevel);
			return;
		}
		int insertLocation = apiLevel - LowestAPILevel - 1;

		specifiedAPILevels[insertLocation] = true;
	}

	public Pair<String, Boolean> getNextAvailableTheme() {
		if (nextAvailableThemeIdx >= usedThemes.size()) {
			nextAvailableThemeIdx = 0;
			return null;
		}
		return usedThemes.get(nextAvailableThemeIdx++);
	}

	private void buildResIdMap() throws Exception {
		id2Name = new HashMap<Integer, String>();
		name2Id = new HashMap<String, Integer>();
		Set<ResPackage> pkgList = resTable.listMainPackages();
		for (ResPackage pkg : pkgList) {
			log.info(String.format("+Looking up for Package: %s ...", pkg.getName()));
			// List<ResType> resTypes = pkg.list

			List<ResResSpec> specs = pkg.listResSpecs();
			for (ResResSpec spec : specs) {
				ResID resId = spec.getId();
				String typename = spec.getType().getName();
				String resName = String.format("%s/%s", typename, spec.getName());
				if (typename.equals("style")) {
					Set<ResResource> resResSet = spec.listResources();
					for (ResResource res : resResSet) {
						addAPILevelInStyle(res.getConfig().getFlags().sdkVersion);
					}
				} else if (typename.equals("layout")) {
					if (goodLayoutFiles == null) {
						goodLayoutFiles = new HashSet<String>();
					}
					goodLayoutFiles.add("res/" + resName + ".xml");
				}
				id2Name.put(resId.id, resName);
				name2Id.put(resName, resId.id);
				log.info(String.format("+Found mapping [ID: 0x%08x] -> [Name: %s]", resId.id, resName));
			}
		}
	}

	/**
	 * Check if resource IDs are successfully extracted
	 *
	 * @return
	 */
	public boolean hasResId() {
		return (id2Name != null && !id2Name.isEmpty());
	}

	/**
	 * Resolve resource name for a given ID.
	 *
	 * @param id
	 * @return null or resource name formatted as '&lt;type&gt;/&lt;name&gt;',
	 *         e.g. 'layout/main'
	 */
	public String resolveNameForId(int id) {
		String name = null;
		if (hasResId())
			name = id2Name.get(id);
		return name;
	}

	/**
	 * Resolve ID for a given resource name as '&lt;type&gt;/&lt;name&gt;'.
	 *
	 * @param name
	 *            e.g. 'layout/main', 'id/hello_btn'
	 * @return null or ID
	 */
	public Integer resolveIdForName(String name) {
		Integer id = null;
		if (hasResId())
			id = name2Id.get(name);
		return id;
	}

	/**
	 * Get ID for a given type and name.
	 *
	 * @param type
	 *            e.g. 'id', 'layout', 'drawable'.
	 * @param name
	 *            e.g. 'hello_btn', 'login_layout'
	 * @return null or ID
	 */
	public Integer resolveIdForName(String type, String name) {
		Integer id = null;
		if (hasResId()) {
			id = name2Id.get(String.format("%s/%s", type, name));
			if (id == null) {
				id = supportName2id.get(type).get(name);
			}
		}
		return id;
	}

	/**
	 * Given an XML file name (e.g. res/layout/login.xml), return the layout
	 * name.
	 *
	 * @param xmlFile
	 *            Must be like this: res/layout/login.xml
	 * @return short layout name, e.g. 'login'.
	 * @throws AssertionError
	 *             if xmlFile is invalid.
	 */
	public String resolveLayoutNameFor(String xmlFile) {
		int len = xmlFile.length();
		int lastSlash = xmlFile.lastIndexOf('/');
		// Misc.ASSERT(lastSlash > 0 && len > 6, "Invalid xmlFile name for
		// layout name: %s", xmlFile);
		return xmlFile.substring(lastSlash + 1, len - 4);
	}

	/**
	 * Get the name for the layout ID. e.g. 'layout/main' for
	 * res/layout/main.xml with ID 0x7f000001.
	 *
	 * @param id
	 * @return null or name (e.g. 'layout/main')
	 * @throws AssertionError
	 *             if invalid layout name is got.
	 */
	private String resolveLayoutForId(int id) {
		String layout = null;
		if (id2Name != null && !id2Name.isEmpty()) {
			layout = id2Name.get(id);
			if (layout != null) {
				// Misc.ASSERT(layout.startsWith("layout/"), "Invalid ID (%08x)
				// for layout. Get: %s.", id, layout);
			}
		}
		return layout;
	}

	private class ThemeIdPair implements Comparable<ThemeIdPair> {
		int ThemeId, UsedTimes;

		ThemeIdPair(int S, int T) {
			ThemeId = S;
			UsedTimes = T;
		}

		@Override
		public int compareTo(ThemeIdPair arg0) {
			int diff = UsedTimes - arg0.UsedTimes;
			if (diff == 0)
				diff = ThemeId - arg0.ThemeId;
			return diff;
		}

		@Override
		public int hashCode() {
			return ThemeId;
		}

		@Override
		public boolean equals(Object that) {
			if (that instanceof ThemeIdPair) {
				return ThemeId == ((ThemeIdPair) that).ThemeId;
			}
			return false;
		}

		@Override
		public String toString() {
			return String.format("%08x:%d", ThemeId, UsedTimes);
		}
	}

	// Return: Language -> String Value. e.g. "en" -> "Hello"
	// in some cases, the resId is systematic ID (e.g. 0x0100xxxx) instead of
	// app-defined ID (0x7f04xxxx)
	public Map<String, String> possibleStringConstantForId(int resId) throws Exception {
		if (null == resTable) {
			log.error("Error resolving String constants in resources.arsc.");
			throw new Exception("Error resolving String constants in resources.arsc.");
		}
		if (resId < 0x7f000000) {
			return Collections.emptyMap();
		}
		Map<String, String> resRet = new TreeMap<String, String>();
		ResResSpec resSpec;
		try {
			resSpec = resTable.getResSpec(resId);
			log.info("Resource #%08X '%s'<", resId, resSpec.getFullName(true, false));
			Set<ResResource> resSet = resSpec.listResources();
			log.info("@: (%d configurations)", resSet.size());
			for (ResResource res : resSet) {
				ResValue resVal = res.getValue();
				if (!(resVal instanceof ResStringValue))
					continue;
				char[] lang = res.getConfig().getFlags().language;
				String language;
				if ('\0' != lang[0])
					language = new String(lang);
				else
					language = DefaultLanguage;
				resRet.put(language, resVal.toString());
			}
		} catch (AndrolibException e) {
			log.error(e);
		}
		return resRet;
	}

	// Return: Language -> String Value. e.g. "en" -> "Hello"
	// only the first main ResPackage with string/<name> resource is returned if
	// there are more than one main package
	public Map<String, String> possibleStringConstantForName(String name) throws Exception {
		if (null == resTable) {
			log.error("Error resolving String constants in resources.arsc.");
			throw new Exception("Error resolving String constants in resources.arsc.");
		}
		Map<String, String> resRet = new TreeMap<String, String>();

		Set<ResPackage> pkgSet = resTable.listMainPackages();
		for (ResPackage pkg : pkgSet) {
			ResTypeSpec resType = null;
			try {
				resType = pkg.getType("string");
			} catch (AndrolibException e) {
				continue;
			}
			boolean resolved = false;
			Set<ResResSpec> specs = resType.listResSpecs();
			for (ResResSpec spec : specs) {
				if (!name.equals(spec.getName()))
					continue;
				Set<ResResource> resSet = spec.listResources();
				for (ResResource res : resSet) {
					ResValue resVal = res.getValue();
					if (!(resVal instanceof ResStringValue))
						continue;
					char[] lang = res.getConfig().getFlags().language;
					String language;
					if ('\0' != lang[0])
						language = new String(lang);
					else
						language = DefaultLanguage;

					resRet.put(language, resVal.toString());
					resolved = true;
				}
				if (resolved)
					break;
			}
			if (resolved)
				break;
		}
		return resRet;
	}

	// may return empty string ""
	private String getAttributeTextValue(AXmlResourceParser parser, String attr) {
		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			if (attrName.equals(attr)) {
				int valueType = parser.getAttributeValueType(i);
				String attributeValue = parser.getAttributeValue(i);
				String attributeValue2 = parser.getAttributeValue(null, "package");

				if (valueType == TypedValue.TYPE_REFERENCE) {
					// ID
					int ID = parser.getAttributeResourceValue(i, 0);
					try {
						Map<String, String> possibleStrVals = possibleStringConstantForId(ID);
						if (!possibleStrVals.isEmpty()) {
							String val = possibleStrVals.get("en");// ENGLISH as
																	// default
																	// choice?
							if (null == val)
								val = possibleStrVals.get(DefaultLanguage);// otherwise
																			// default
																			// language
							// fixed here: replace "else"
							if (null == val)
								val = "";
							return val;
						}
					} catch (Exception e) {
						log.error("Fail resolving string values for <%s %s=0x%08x .../>", parser.getName(), attrName,
								ID);
					}
				} else if (valueType == TypedValue.TYPE_STRING) {
					return attributeValue;
				} else {
					log.debug("Unexpected value type for <%s %s=[TYPE:0x%08x] .../>", parser.getName(), attrName,
							valueType);
				}
			}
		}
		return "";
	}

	private int getAttributeInputType(AXmlResourceParser parser, String attr) {

		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			if (attrName.equals(attr)) {
				if (TypedValue.TYPE_INT_HEX == parser.getAttributeValueType(i)) {
					return parser.getAttributeIntValue(i, 0);
				}
			}
		}
		return 0;
	}

	private boolean getAttributeBoolValue(AXmlResourceParser parser, String attr) {

		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			if (attrName.equals(attr)) {
				if (TypedValue.TYPE_INT_BOOLEAN == parser.getAttributeValueType(i)) {
					return parser.getAttributeBooleanValue(i, false);// IntValue(i,
																		// 0);
				}
			}
		}
		return false;
	}

	private int getAttributeIdValue(AXmlResourceParser parser, String attr) {
		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			if (attrName.equals(attr)) {
				return parser.getAttributeResourceValue(i, 0);
			}
		}
		return 0;
	}

	private boolean validId(int id) {
		return id != 0 && id != -1;
	}

	/**
	 * Check if XML is defined as a layout resource in resources.arsc. In some
	 * cases, some XMLs in res/layout are not defined in the resources.arsc.
	 *
	 * @param xml
	 *            Full name (e.g. res/layout/main.xml).
	 * @return
	 */
	private boolean isGoodLayoutFile(String xml) {
		return goodLayoutFiles != null && goodLayoutFiles.contains(xml);
	}

	public void setTagHandler(TagXmlHandler handler) {
		this.taghandler = handler;
	}

	// <include .../> has not been handled
	private void handleSingleXML(String name, ZipEntry xml) {
		try {
			log.info("Try parsing layout XML '%s'...", name);
			InputStream xmlIS = apkAchive.getInputStream(xml);
			AXmlResourceParser parser = new AXmlResourceParser();
			parser.open(xmlIS);
			int type = -1;
			Stack<ResLayout> state = new Stack<ResLayout>();
			ResLayout peekNode = null, currNode = null;
			String[] namesplit = name.split("/");
			// PrintWriter out = new
			// PrintWriter("textoutput/"+namesplit[namesplit.length-1] +
			// "_output.txt");

			if (taghandler != null) {
				taghandler.startOneXml(namesplit[namesplit.length - 1]);
			}
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
				switch (type) {
				case XmlPullParser.START_DOCUMENT:
					if (file2Nodes == null)
						file2Nodes = new HashMap<String, List<ResLayout>>();
					file2Nodes.put(name, null);
					break;
				case XmlPullParser.START_TAG:
					if (!state.isEmpty())
						peekNode = state.peek();

					currNode = handleOneTag(name, parser, peekNode);
					if (taghandler != null) {
						taghandler.setResTable(resTable);
						taghandler.setId2Name(id2Name);
						taghandler.handleOneTag(name, parser, peekNode);
					}
					// Misc.ASSERT(currNode != null);
					state.push(currNode);
					break;
				case XmlPullParser.END_TAG:
					state.pop();
					break;
				case XmlPullParser.END_DOCUMENT:
					// Misc.ASSERT(state.isEmpty(), "Error passing '%s': the
					// state stack is non-empty at the end.", name);
					break;
				}
			}
			parser.close();

			if (taghandler != null) {
				taghandler.endOneXml(namesplit[namesplit.length - 1]);
			}

		} catch (Throwable e) {
			if (file2Nodes != null)
				file2Nodes.remove(name);
			// Logger.logExcept(e, "Error handling XML '%s'", name);
			log.error("Error handling XML '" + name + "'", e);
		}
	}

	// INCLUDED: e.g. 'layout/included' for 'res/layout/included.xml'
	private void addIncluded2XML(String included /* full layout name */,
			String xmlFile /* full name */) {
		String fullIncluded = String.format("res/%s.xml", included);
		if (included2XMLs == null)
			included2XMLs = new HashMap<String, Set<String>>();
		Set<String> enclosingXMLs = included2XMLs.get(fullIncluded);
		if (enclosingXMLs == null) {
			enclosingXMLs = new HashSet<String>();
			included2XMLs.put(fullIncluded, enclosingXMLs);
		}
		enclosingXMLs.add(xmlFile);
	}

	private void addResLayoutNode(String xmlFile, int id, ResLayout node) {
		List<ResLayout> nodesInFile, nodesForId;

		nodesInFile = file2Nodes.get(xmlFile);
		if (nodesInFile == null) {
			nodesInFile = new LinkedList<ResLayout>();
			file2Nodes.put(xmlFile, nodesInFile);
		}
		nodesInFile.add(node);

		if (validId(id)) {
			if (id2Node == null)
				id2Node = new TreeMap<Integer, List<ResLayout>>();
			nodesForId = id2Node.get(id);
			if (nodesForId == null) {
				nodesForId = new LinkedList<ResLayout>();
				id2Node.put(id, nodesForId);
			}
			nodesForId.add(node);
		}
	}

	/**
	 * Label nodeId as sensitive. If nodeId == 0 or -1(0xFFFFFFFF), it is
	 * ignored. This method can also be used to add new fields after UI elements
	 * are recognized to contain sensitive data.
	 *
	 * @param nodeId
	 * @return TRUE if successfully set.
	 */
	public boolean setAsSensitiveField(int nodeId) {
		if (!validId(nodeId))
			return false;
		if (sensitiveFields == null) {
			sensitiveFields = new TreeSet<Integer>();
		}
		return sensitiveFields.add(nodeId);
	}

	public class ResLayout {
		public final String file;// enclosing XML file
		public final int id;// read ID for component. many may be 0 if they
							// don't have an associated ID
		public final String tag;// Tag name of XML node, e.g. 'Button' in
								// <Button .../>
		public boolean sensitive;
		// private List<ResLayout> parents;
		private String defaultText = null;
		private String dynamicText = null;
		private ResLayout parent;// even if some node with same ID can be put
									// into different XMLs
		private List<ResLayout> children;

		ResLayout(String file, String tag, int id, String text, boolean sensitive, ResLayout parent) {
			this.file = file;
			this.tag = tag;
			this.id = id;
			this.defaultText = text;
			this.sensitive = sensitive;
			this.children = new LinkedList<ResLayout>();
			this.parent = parent;
			if (null != parent) {
				parent.children.add(this);
			}
		}

		public ResLayout getParentNode() {
			return parent;
		}

		public Iterator<ResLayout> iterateChildren() {
			return children.iterator();
		}

		@Override
		public int hashCode() {
			String hashStr = String.format("%s-%s-%08x", file, tag, id);
			return hashStr.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ResLayout) {
				ResLayout that = (ResLayout) obj;
				if (file.equals(that.file) && id == that.id && tag.equals(that.tag))
					return true;
			}
			return false;
		}

		public String getText() {
			if (dynamicText == null)
				return defaultText;
			else
				return dynamicText;
		}

		/**
		 * Modify associated TEXT for the node-related component if methods like
		 * '&lt;view&gt;.setText(...)' are called.
		 *
		 * @param dynText
		 *            Dynamic text value in the App code.
		 */
		public void setText(String dynText) {
			dynamicText = dynText;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(file);
			builder.append(": <");
			builder.append(tag);
			builder.append(" ID=");
			if (id != 0)
				builder.append(String.format("%08x", id));
			else
				builder.append(id);
			builder.append(", TEXT=");
			builder.append(getText());
			builder.append(" />");
			return builder.toString();
		}
	}

	private TagXmlHandler taghandler;

	private ResLayout handleOneTag(String xmlFile, AXmlResourceParser parser, ResLayout parent) {
		String tagName = parser.getName();
		// in USAA, a layout file has root node <selector>. This file actually
		// should be in drawable-xxxx/ and it is a
		// copy of a file in drawable-mdip/. This file cannot be rendered as a
		// layout. So ignore such kind of files.
		if (parent == null && tagName.equals("selector")) {
			return null;
		}
		// quite a few use 'tag' instead of 'id' in the xml, especially for
		// <fragment>.
		// they can be referenced by FragmentManager.findFragmentByTag().
		int tagId = getAttributeIdValue(parser, "id");
		if (tagName.equals("view")) {// custom view can use "class" to indicate
										// the class
			String klass = getAttributeTextValue(parser, "class");
			if (!klass.isEmpty()) {
				tagName = klass;
			}
		} else if (tagName.equals("fragment")) {
			String klass = getAttributeTextValue(parser, "name");
			if (!klass.isEmpty()) {
				// the fragment is just simply mocked via a MockView in render
				// process as in ADT plugin
				tagName = String.format("%s::%s", tagName, klass);
			}
		} else if (tagName.equals("include")) {
			int incId = getAttributeIdValue(parser, "layout");
			// link the <include ...> tag to the included layout file via the
			// ID.
			// but in some cases, 'id' also exists. How to handle? Maybe
			// useless.
			if (!validId(tagId))
				tagId = incId;
			String layout = resolveLayoutForId(incId);
			if (null != layout)
				addIncluded2XML(layout, xmlFile);
		}
		String tagText = getAttributeTextValue(parser, "text");
		if (null != tagText)
			tagText = tagText.trim();
		if (null == tagText || tagText.isEmpty()) {
			tagText = getAttributeTextValue(parser, "hint");
		}
		int tagInputType = getAttributeInputType(parser, "inputType");
		boolean isPwd = getAttributeBoolValue(parser, "password");
		log.info("+TAG: %s [ID: %08x , TEXT: \"%s\"] (%s)", tagName, tagId, tagText, xmlFile);
		log.info("*@<");

		boolean sensitive = SensitiveInputField.validate(tagInputType, isPwd);
		ResLayout node = new ResLayout(xmlFile, tagName, tagId, tagText, sensitive, parent);
		addResLayoutNode(xmlFile, tagId, node);
		if (validId(tagId)) {
			// if (validId(tagId) && editableNode(tagName)) {
			addEditableField(tagId);
			if (sensitive) {
				setAsSensitiveField(tagId);
			} // else {//TODO: currently add all XML with editable node
			addEditableXML(xmlFile);
			// }
		}

		////////////////////////
		if (labelWriter != null) {
			if (tagText != null || sensitive) {
				String prefix = "";
				if (sensitive)
					prefix = "[PwdLike] ";
				String toWrite = String.format("{%s}: 0x%08x : %s\n\t%s\n", xmlFile, tagId, tagName, prefix + tagText);
				try {
					labelWriter.write(toWrite);
					labelWriter.flush();
				} catch (Exception e) {
				}
			}
		}
		////////////////////////
		return node;
	}

	public boolean addEditableXML(String xml) {
		if (editableXMLs == null) {
			editableXMLs = new HashSet<String>();
		}
		return editableXMLs.add(xml);
	}

	private void addEditableField(int resId) {
		if (editableFields == null) {
			editableFields = new TreeSet<Integer>();
		}
		editableFields.add(Integer.valueOf(resId));
	}

	private void extractLayoutXML(Map<String, ZipEntry> layoutEntries) {
		Set<Map.Entry<String, ZipEntry>> entrySet = layoutEntries.entrySet();
		for (Map.Entry<String, ZipEntry> entry : entrySet) {
			String name = entry.getKey();
			ZipEntry xml = entry.getValue();
			log.info("handling %s,%s", name, xml);
			if (isGoodLayoutFile(name))
				handleSingleXML(name, xml);
		}
		postHandleLayoutXML();
		if (taghandler != null) {			
			taghandler.endOneApp(apkfilename);
		}
	}

	// propagate 'editable' from included XMLs to the top level XMLs
	private void postHandleLayoutXML() {
		if (editableXMLs != null && included2XMLs != null) {
			Set<String> handledIncluded = new HashSet<String>();
			int sizeIncluded;
			do {
				sizeIncluded = included2XMLs.size();
				Set<Map.Entry<String, Set<String>>> entries = included2XMLs.entrySet();
				for (Map.Entry<String, Set<String>> entry : entries) {
					String included = entry.getKey();
					Set<String> enclosing = entry.getValue();
					// included XMLs will not be rendered. only the top level is
					// concerned
					if (editableXMLs.remove(included)) {
						for (String xml : enclosing) {
							editableXMLs.add(xml);
						}
						handledIncluded.add(included);
					}
				}
				for (String added : handledIncluded) {
					included2XMLs.remove(added);
				}
				handledIncluded.clear();
			} while (sizeIncluded != included2XMLs.size() && !included2XMLs.isEmpty());
			handledIncluded = null;
		}
		included2XMLs = null;
	}

	public HashMap<Integer, String> getIdToName() {
		return id2Name;
	}

}
