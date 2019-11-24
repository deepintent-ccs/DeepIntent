

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ImageToWidgetsAnalyzer {

	public static void main(String[] args) throws Exception {

		String widimages = args[0];
		String gator = args[1];
		String outputfolder =  args[2] + "img2widgets/";
		List<String> apks = Files.readAllLines(Paths.get( args[3]));
		ImageToWidgetsAnalyzer analyzer = new ImageToWidgetsAnalyzer();
		analyzer.generateImageToMethods(apks,widimages, gator, outputfolder);
		System.out.println("Finish the image to method mapping generation.");
	}

	class APKTask implements Callable<Integer> {

		private String widimages;
		private String apk;
		private JSONParser parser = new JSONParser();
		private String gator;
		private String outputfolder;
		private HashMap<ImageData, HashSet<WidgetID>> img2widgets = new HashMap<>();
		public HashMap<WidgetID, Set<String>> w2handlers;

		public APKTask(String widimages, String apk, String gator, String outputfolder) {
			super();
			this.widimages = widimages;
			this.apk = apk;
			this.gator = gator;
			this.outputfolder = outputfolder;
		}

		@Override
		public Integer call() throws Exception {
			try {
				String widjson = new String(Files.readAllBytes(Paths.get(widimages + "/" + apk + ".json")));
				JSONArray xml2images = (JSONArray) parser.parse(widjson);
				for (Object x : xml2images) {
					JSONObject xml = (JSONObject) x;
					JSONArray tags = (JSONArray) xml.get("tags");
					String layout = (String) xml.get("name");
					if (tags.size() > 0) {
						for (Object y : tags) {
							JSONObject tag = (JSONObject) y;
							long tagid = (Long) tag.get("id");
							if (tag.get("idName") == null) {
								continue;
							}
							String tagIdName = ((String) tag.get("idName")).substring(3);
							WidgetID wid = new WidgetID(tagid, tagIdName, layout);
							JSONArray imgs = (JSONArray) tag.get("imgs");
							for (Object z : imgs) {
								JSONObject img = (JSONObject) z;
								if (!((String) img.get("attribute")).equals("background")) {
									String drawable = ((String) img.get("value")).substring(9);
									ImageData i = new ImageData(apk, drawable);
									if (!img2widgets.containsKey(i)) {
										img2widgets.put(i, new HashSet<WidgetID>());
									}
									img2widgets.get(i).add(wid);
								}
							}
						}
					}
				}

				// gator
				if (!Files.exists(Paths.get(gator + "/" + apk + ".apk.json"))) {
					return 1;
				}

				try {
					String gatorjson = new String(Files.readAllBytes(Paths.get(gator + "/" + apk + ".apk.json")));
					JSONArray view2handlers = (JSONArray) parser.parse(gatorjson);
					w2handlers = new HashMap<>();
					for (Object x : view2handlers) {
						JSONObject v2handler = (JSONObject) x;
						JSONArray views = (JSONArray) v2handler.get("views");
						String windowname = (String) v2handler.get("name");
						String layoutname = "gator";
						if (windowname.contains("LID")) {
							String layoutString = windowname.split("LID\\[")[1].split("\\]")[0];
							Long layoutid = Long.parseLong(layoutString.split("\\|")[0]);
							layoutname = layoutString.split("\\|")[1];
							layoutname = layoutname.replace("layout_", "") + ".xml";
							System.out.println("layoutname:" + layoutname);
						}

						for (Object y : views) {
							JSONObject view = (JSONObject) y;
							String viewname = (String) view.get("name");
							System.out.println("viewname: " + viewname);
							if (viewname.contains("WID[")) {
								String widstring = viewname.split("WID\\[")[1].split("\\]")[0];
								Long wid = Long.parseLong(widstring.split("\\|")[0]);
								String widname = widstring.split("\\|")[1];
								WidgetID nwid = new WidgetID(wid, widname, layoutname);
								if (!w2handlers.containsKey(nwid)) {
									w2handlers.put(nwid, new HashSet<>());
								}

								JSONArray handlers = (JSONArray) view.get("handlers");
								for (Object z : handlers) {
									JSONObject e2handlers = (JSONObject) z;

									for (Object k : (JSONArray) e2handlers.get("handlers")) {
										w2handlers.get(nwid).add((String) k);
									}

								}

								JSONArray w2images = (JSONArray) view.get("images");
								for (Object z : w2images) {
									String drawableimageid = (String) z; // "DrawableID[2130837509|drawable_btn_next]585",
									Long imageid = Long.parseLong(drawableimageid.split("\\[")[1].split("\\|")[0]);
									String imagename = drawableimageid.split("\\[")[1].split("\\|")[1].split("\\]")[0];
									if (imagename.startsWith("drawable_")) {
										imagename = imagename.substring(9);
										ImageData i = new ImageData(apk, imagename);
										if (!img2widgets.containsKey(i)) {
											img2widgets.put(i, new HashSet<>());
										}
										img2widgets.get(i).add(nwid);
									}
								}
							}
						}

					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				String widimagejson = new String(Files.readAllBytes(Paths.get(widimages + "/" + apk + ".image.json")));
				JSONArray r2images = (JSONArray) parser.parse(widimagejson);
				HashMap<ImageData, HashSet<ImageData>> imagegroupmap = new HashMap<>();

				for (Object x : r2images) {
					JSONObject r2image = (JSONObject) x;
					String rname = (String) r2image.get("name");
					ImageData i = new ImageData(apk, rname);
					JSONArray rimages = (JSONArray) r2image.get("images");
					HashSet<ImageData> rimageset = new HashSet<>();
					rimageset.add(i);
					imagegroupmap.put(i, rimageset);
					for (Object y : rimages) {
						String imagename = (String) y;
						ImageData e = new ImageData(apk, imagename);
						rimageset.add(e);
						imagegroupmap.put(e, rimageset);
					}

					if (img2widgets.containsKey(i)) {
						HashSet<WidgetID> widset = img2widgets.get(i);
						for (Object y : rimages) {
							String imagename = (String) y;
							img2widgets.put(new ImageData(apk, imagename), widset);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return 0;

		}

	}

	public void generateImageToMethods(List<String> apks, String widimages, String gator, String outputfolder)
			throws Exception {

		// apks.addAll(Files.readAllLines(Paths.get("selectedAPK.txt")));
		final ExecutorService service = Executors.newSingleThreadExecutor();


		for (String apk : apks) {
			PrintWriter out = new PrintWriter(outputfolder + "/" + apk + "_img2widgets.csv");
			out.println("APK\tImage\tWID\tWID Name\tLayout\tHandler");

			try {

				APKTask apkTask = new APKTask(widimages, apk, gator, outputfolder);
				service.invokeAll(Arrays.asList(apkTask), 10, TimeUnit.MINUTES);
				for (Entry<ImageData, HashSet<WidgetID>> e : apkTask.img2widgets.entrySet()) {
					for (WidgetID wid : e.getValue()) {
						out.print(apk + "\t");
						out.print(e.getKey().name + "\t");
						out.print(wid.id + "\t");
						out.print(wid.idName + "\t");
						out.print(wid.layout + "\t");
						out.print("[");
						if (apkTask.w2handlers.get(wid) != null){
							for (String handler : apkTask.w2handlers.get(wid)) {
								out.print(handler + "|");
							}
						}
						out.println("]");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println(apk + " cannot be analyzed");
			}
			out.close();
		}
		service.shutdown();
	}

}
