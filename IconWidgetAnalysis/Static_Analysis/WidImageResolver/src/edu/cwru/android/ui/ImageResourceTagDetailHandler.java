package edu.cwru.android.ui;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.util.TypedValue;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.data.value.ResValue;
import brut.androlib.res.decoder.AXmlResourceParser;
import edu.cwru.android.ui.APKResourceResolver.ResLayout;

public class ImageResourceTagDetailHandler extends TagXmlHandler {

	private static org.apache.logging.log4j.Logger log = LogManager
			.getFormatterLogger(ImageResourceTagDetailHandler.class);

	public final static String DefaultLanguage = "##";
	private Set<String> tagsToInspect = new HashSet<>();
	private JSONArray views = new JSONArray();
	private JSONObject currentview;

	@Override
	public void endOneApp(String apkfilename) {
		out.println(views.toJSONString());
	}

	public ImageResourceTagDetailHandler() {
	}

	public ImageResourceTagDetailHandler(PrintWriter out) {
		this.out = out;
	}

	public void addTagsToInspect(String tag) {
		tagsToInspect.add(tag);
	}

	@Override
	public void startOneXml(String xmlname) {
		JSONObject view = new JSONObject();
		view.put("name", xmlname);
		view.put("tags", new JSONArray());
		views.add(view);
		currentview = view;
	}

	@Override
	public void endOneXml(String xmlname) {
		super.endOneXml(xmlname);

	}

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

	private int getAttributeIdValue(AXmlResourceParser parser, String attr) {
		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			if (attrName.equals(attr)) {
				return parser.getAttributeResourceValue(i, 0);
			}
		}
		return 0;
	}

	public static void main(String[] args) throws Exception {

		if (args.length != 1) {
			System.out.println("Please give a directory for the apks to be analyzed");
			return;
		}

		class ValueComparator implements Comparator<String> {
			Map<String, Integer> base;

			public ValueComparator(Map<String, Integer> base) {
				this.base = base;
			}

			// Note: this comparator imposes orderings that are inconsistent
			// with
			// equals.
			public int compare(String a, String b) {
				if (base.get(a) >= base.get(b)) {
					return -1;
				} else {
					return 1;
				} // returning 0 would merge keys
			}
		}

		File dir = new File(args[0]);
		Map<String, Integer> stats = new HashMap<>();
		Map<String, Integer> results = new TreeMap<String, Integer>(new ValueComparator(stats));
		Queue<File> queue = new LinkedList<>();
		Arrays.asList(dir.listFiles()).forEach(f -> queue.offer(f));
		HashSet<String> failedfiles = new HashSet<>();
		int count = 0;
		while (!queue.isEmpty()) {
			File apkfile = queue.poll();
			if (apkfile.isDirectory()) {
				Arrays.asList(apkfile.listFiles()).forEach(f -> queue.offer(f));
				continue;
			}
			if (!apkfile.getName().endsWith("apk")) {
				continue;
			}

			String filename = apkfile.getAbsolutePath();
			System.out.println("Analyzing " + filename);

			try {
				APKResourceResolver resolver = new APKResourceResolver(filename);
				TagXmlHandler handler = new ImageResourceTagDetailHandler();
				resolver.setTagHandler(handler);
				resolver.extractARSCAndLayoutFiles();

				System.out.println("Done analyzing " + filename);
				count++;
			} catch (Exception e) {
				failedfiles.add(filename);
			}
		}

		System.out.println("Successfully process " + count + " files");

		PrintWriter out = new PrintWriter("stats.txt");
		out.println("Class, Count");
		results.putAll(stats);
		for (Entry<String, Integer> e : results.entrySet()) {
			out.println(e.getKey() + "," + e.getValue());
		}
		out.close();

		PrintWriter out2 = new PrintWriter("failed.txt");
		for (String fail : failedfiles) {
			out2.println(fail);
		}
		out2.close();

	}

	@Override
	public ResLayout handleOneTag(String xmlFile, AXmlResourceParser parser, ResLayout parent) {

		String tagName = parser.getName();
		log.info("handling " + tagName + " in " + xmlFile);
		int tagId = getAttributeIdValue(parser, "id");

		if (tagsToInspect.size() > 0 && !tagsToInspect.contains(tagName)) {
			return parent;
		}

		if (out == null) {
			return parent;
		}

		boolean found = false;
		ArrayList<String> values = new ArrayList<>();

		List<String> imgsrc = new ArrayList<String>();
		List<String> imgvalue = new ArrayList<String>();

		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String attrName = parser.getAttributeName(i);
			int valueType = parser.getAttributeValueType(i);
			String value = parser.getAttributeValue(i);

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
						value = val;
					}
				} catch (Exception e) {
					log.error("Fail resolving string values for <%s %s=0x%08x .../>", parser.getName(), attrName, ID);
				}
			} else if (valueType == TypedValue.TYPE_STRING) {
				value = parser.getAttributeValue(i);

			}

			if (value != null) {
				if (valueType == TypedValue.TYPE_REFERENCE && value.startsWith("@")) {
					String temp = id2Name.get(Integer.parseInt(value.replace("@", "")));
					if (temp != null) {
						value = temp;
					}
				}

				if (value.startsWith("drawable")) {
					found = true;
					imgsrc.add(attrName);
					imgvalue.add(value);
				}
			}

			values.add(attrName + "=" + value);
		}

		if (found) {

			JSONObject tag = new JSONObject();
			tag.put("name", tagName);
			tag.put("id", tagId);
			tag.put("idName", id2Name.get(tagId));
			JSONArray imgs = new JSONArray();
			tag.put("imgs", imgs);
			((JSONArray) currentview.get("tags")).add(tag);
			// out.print(tagName + "-> [id=" + tagId);
			// out.print(" (" + id2Name.get(tagId)+")");

			for (int j = 0; j < imgsrc.size(); j++) {
				// out.print(", "+imgsrc.get(j) + "=" + imgvalue.get(j));
				JSONObject img = new JSONObject();
				img.put("attribute", imgsrc.get(j));
				img.put("value", imgvalue.get(j));
				imgs.add(img);
			}

			// out.println("]");
			// out.flush();
		}

		return parent;
	}

}
