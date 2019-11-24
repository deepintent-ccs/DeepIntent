/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.cwru.android.ui;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.io.FolderWrapper;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.utils.Pair;

import android.content.pm.PackageParser.Component;
import android.view.View;
//import edu.cwru.android.ui.correlation.UICorrelation;
import edu.cwru.android.ui.correlation.UIKeywordFactory;

import org.apache.logging.log4j.LogManager;
import org.jgrapht.DirectedGraph;
import org.jgrapht.ext.ComponentNameProvider;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.imageio.ImageIO;

/**
 * Sample code showing how to use the different API used to achieve a layout
 * rendering. This requires the following jar: layoutlib-api.jar, common.jar,
 * sdk-common.jar, sdklib.jar (although we should get rid of this one) and a
 * full SDK (or at least the platform component).
 *
 */
public class APKRenderer {

	// path to the SDK and the project to render
	private static String SDK = "/Users/xushengxiao/Android/platform/android-22";
	private static String PROJECT = "...<insert>...";
	private static org.apache.logging.log4j.Logger log = LogManager.getFormatterLogger(APKRenderer.class);
	private Set<String> layoutNames = null;
	private Set<String> failedLayout;
	private boolean errorForCurrentLayout;
	private boolean useProvidedLayout;
	private FileWriter coorWriter;
	private boolean DEBUG = false;
	private boolean renderUI = true;
	private APKResourceResolver resolver;
	private HashSet<String> renderedLayouts;
	private HashSet<Integer> annotationViews;
	private HashSet<Integer> editBoxes;

	public APKRenderer(String sdk) {
		this.SDK = sdk;
	}

	private static class ApkTool {
		static void decodeApk(String apk, String dstDir) {
			Process proc;
			try {
				proc = new ProcessBuilder().command("lib/apktool", "d", "-f", "-s", apk, "-o", dstDir).start();
				boolean repeat = true;
				int exit = -1;
				while (repeat) {
					try {
						exit = proc.exitValue();
						repeat = false;
					} catch (Exception e) {
					}
				}
			} catch (IOException e1) {
				log.error(e1);
			}
		}

		static void removeTemp(String dstDir) {
			Process proc;
			try {
				proc = new ProcessBuilder().command("rm", "-r", "-f", dstDir).start();
				boolean repeat = true;
				int exit = -1;
				while (repeat) {
					try {
						exit = proc.exitValue();
						repeat = false;
					} catch (Exception e) {
					}
				}
			} catch (IOException e1) {
				log.error(e1);
			}
		}
	}

	/**
	 * Build a hierarchy Graph for current layout. If current layout has
	 * multiple top level elements (e.g. &lt;merge&gt; is the root node of the
	 * XML file), an artificial {@link UIElement} node is built as the root of
	 * the graph.
	 *
	 * @param session
	 * @return a {@link Graph} representation of current layout
	 */
	private DirectedGraph<UIElement, DefaultEdge> buildUiHierarchy(RenderSession session) {
		List<ViewInfo> topLevels = session.getRootViews();
		UIElement root = null;
		UIElement.NODENBR = 0;
		DirectedGraph<UIElement, DefaultEdge> uiHierarchy = new DefaultDirectedGraph<UIElement, DefaultEdge>(
				DefaultEdge.class);
		if (topLevels.size() > 1) {
			root = new UIElement();
			uiHierarchy.addVertex(root);
		}
		buildUiHierarchyEx(uiHierarchy, root, topLevels);
		return uiHierarchy;
	}

	private void buildUiHierarchyEx(DirectedGraph<UIElement, DefaultEdge> uiHierarchy, UIElement parent,
			List<ViewInfo> views) {
		UIElement element;
		for (ViewInfo info : views) {
			element = new UIElement();
			element.left = info.getLeft();
			element.top = info.getTop();
			element.right = info.getRight();
			element.bottom = info.getBottom();
			element.className = info.getClassName();
			View view = (View) info.getViewObject();
			element.view = view;
			element.id = view.getId();
			element.paddingLeft = view.getPaddingLeft();
			element.paddingTop = view.getPaddingTop();
			element.paddingRight = view.getPaddingRight();
			element.paddingBottom = view.getPaddingBottom();
			element.visibility = view.getVisibility();
			element.height = view.getHeight();
			element.width = view.getWidth();
			if (view instanceof android.widget.TextView) {
				CharSequence text = ((android.widget.TextView) view).getText();
				CharSequence hint = ((android.widget.TextView) view).getHint();
				String textEx = "", hintEx = "";
				if (text != null)
					textEx = text.toString().trim();
				if (hint != null)
					hintEx = hint.toString().trim();
				if (!textEx.isEmpty() && !hintEx.isEmpty()) {
					element.text = String.format("%s; %s", textEx, hintEx);
				} else if (!textEx.isEmpty()) {
					element.text = textEx;
				} else if (!hintEx.isEmpty()) {
					element.text = hintEx;
				}
			}
			if (view instanceof android.widget.EditText) {
				element.inputType = ((android.widget.EditText) view).getInputType();
			}
			uiHierarchy.addVertex(element);
			if (parent != null) {
				uiHierarchy.addEdge(parent, element);
			}
			List<ViewInfo> children = info.getChildren();
			if (children != null) {
				buildUiHierarchyEx(uiHierarchy, element, children);
			}
		}
	}

	class UIElementNameProvider implements ComponentNameProvider<UIElement> {

		@Override
		public String getName(UIElement e) {
			return e.toString();
		}

	}

	class UIElementIdProvider implements ComponentNameProvider<UIElement> {
		@Override
		public String getName(UIElement e) {
			return "" + e.nodeid;
		}

	}

	private void renderUiInBatchMode(RenderService renderService, FolderConfiguration config,
			APKResourceResolver resMgr, String themeName) {
		log.info("Will render: %d files", layoutNames.size());
		failedLayout = new HashSet<String>();
		for (String layoutName : layoutNames) {
			try {
				String layout = layoutName;
				errorForCurrentLayout = false;
				if (!useProvidedLayout)
					layout = resMgr.resolveLayoutNameFor(layoutName);
				log.info("Rendering layout for: %s", layout);
				RenderSession session = renderService.setAppInfo(layout, "icon") // optional
						.createRenderSession(layout /* layoutName */);
				if (errorForCurrentLayout) {
					throw new Exception();
				}
				// SparseNumberedGraph<UIElement> uiHierarchy = null;
				// get the status of the render
				PrintWriter fout = new PrintWriter("sentext_output/uisensitive" + layout + ".txt");
				Result result = session.getResult();
				if (result.isSuccess()) {
					DirectedGraph<UIElement, DefaultEdge> uiHierarchy = buildUiHierarchy(session);
					HashMap<Integer, UIElement> id2nodemap = new HashMap<>();
					DirectedGraph<UIElement, DefaultEdge> activeUiHierarchy = duplicateWithAbsCoordinate(uiHierarchy,
							id2nodemap);

					ComponentNameProvider<UIElement> provider = new UIElementNameProvider();
					DOTExporter<UIElement, DefaultEdge> exporter = new DOTExporter<UIElement, DefaultEdge>(
							new UIElementIdProvider(), provider, null);
					exporter.exportGraph(uiHierarchy, new FileWriter("dot_output/" + layout + ".dot"));

					for (UIElement ele : uiHierarchy.vertexSet()) {
						fout.println("id: " + ele.id + " res id: " + resolver.resolveNameForId(ele.id));
						System.out.println("id: " + ele.id + " res id: " + resolver.resolveNameForId(ele.id));
					}
					renderedLayouts.add(layout);

					if (layout.contains("main2")) {
						System.out.println("main2");
					}

					postBuildUiHierarchy(layout, uiHierarchy);
					// System.out.println(uiHierarchy);
					// addLayout2Hierarchy(layout, uiHierarchy);

					for (Integer id : editBoxes) {
						UIElement editbox = id2nodemap.get(id);
						int editBoxId = editbox.id;
						String tag;
						if ((tag = UIKeywordFactory.sensitive(editbox.text)) != null) {
							fout.println("sensitive editbox: " + editBoxId + " res id: "
									+ resolver.resolveNameForId(editBoxId) + " tag: " + tag);
						}
					}
					
				}
				fout.close();

				// if (result.isSuccess() &&
				// ifContainGoneViews(session.getRootViews())) {
				// result = session.render();
				// }
				if (result.isSuccess() == false) {
					// System.err.println(result.getErrorMessage() + " ");
					// result.getException().printStackTrace();
					// System.exit(1);
					log.error("rendering failed for " + layoutName + "with theme " + themeName + " due to "
							+ result.getErrorMessage());
					log.error("Exception: ", result.getException());
					failedLayout.add(layoutName);
					log.info("[Warning] Failed to render layout: %s", layoutName);
					continue;
				}

				///////////////////////////
				// if (null != coorWriter) {
				// try {
				// coorWriter.write("{layout}: " + layout + "\n");
				// } catch (Exception ee) {}
				// Iterator<UIElement> iter = activeUiHierarchy.iterator();
				// while (iter.hasNext()) {
				// UIElement ele = iter.next();
				// try {
				// String toWrite = String.format(" 0x%08x : [%s] [%d, %d, %d,
				// %d]\n\t%s\n",
				// //ele.id, ele.className, ele.left + ele.paddingLeft, ele.top
				// - ele.paddingTop,
				// //ele.right - ele.paddingRight, ele.bottom -
				// ele.paddingBottom, ele.text);
				// ele.id, ele.className, ele.left, ele.top,
				// ele.right, ele.bottom, ele.text);
				// coorWriter.write(toWrite);
				// coorWriter.flush();
				// } catch (Exception ee) {ClassLog.logExcept(ee);}
				// }
				// try {
				// coorWriter.write("\n\n");
				// } catch (Exception ee) {}
				// }
				//////////////////////////
				// Map<Integer, int[]> uiScores =
				// UICorrelation.compute(editBoxes, annotationViews,
				// activeUiHierarchy);
				// Set<Map.Entry<Integer, int[]>> entries = uiScores.entrySet();
				// for (Map.Entry<Integer, int[]> entry : entries) {
				// int editBoxGraphId = entry.getKey().intValue();
				// int editBoxId = activeUiHierarchy.getNode(editBoxGraphId).id;
				// //ClassLog.Info("ID:0x%08x \n", editBoxId);
				// int[] scores = entry.getValue();
				// for (int i = 0; i < scores.length; i++) {
				//// System.out.format("\t0x%08x : %s\n",
				// activeUiHierarchy.getNode(scores[i]).id,
				//// activeUiHierarchy.getNode(scores[i]).text);
				// if (i >= 1/*3*/) //only the first 3 are considered
				// break;
				// String text = activeUiHierarchy.getNode(scores[i]).text;
				// String tag;
				// if (text != null && (tag = UIKeywordFactory.sensitive(text))
				// != null) {
				// addSensitiveField(editBoxId, tag);
				// addSensitiveField2Label(editBoxId, layout, text);
				// //if(!has_se) {has_se = true; v_se ++;}
				// break;
				// }
				// }
				// }
				// System.out.println(annotationViews + "\n" + editBoxes);
				// System.out.println("CLONED:\n" + activeUiHierarchy);
				// System.out.println("Score [2->3] = " + computeScore(2, 3));
				if (renderUI && result.isSuccess()) {
					// get the image and save it somewhere.
					BufferedImage image = session.getImage();
					String targetPNG = String.format("ui_output/test-%s.png", layout);
					ImageIO.write(image, "png", new File(targetPNG));

					// read the views
					if (layoutNames.size() == 1) {
						displayViewObjects(session.getRootViews());
					}
				}

			} catch (Exception e) {
				log.error(e);
				e.printStackTrace();
			}

		}
	}

	private void postBuildUiHierarchy(String layout, DirectedGraph<UIElement, DefaultEdge> uiHierarchy) {
		// TODO Auto-generated method stub
		annotationViews = new HashSet<Integer>();
		editBoxes = new HashSet<Integer>();
		Set<UIElement> vertexSet = uiHierarchy.vertexSet();
		for (UIElement ele : vertexSet) {
			if (ele.className != null && ele.className.equals("android.widget.TextView")) {
				// make sure the TextView is not zero size.
				if (ele.right > ele.left && ele.bottom > ele.top)
					annotationViews.add(ele.nodeid);
			} else if (ele.view instanceof android.widget.EditText || ele.view instanceof android.widget.RadioButton
					|| ele.view instanceof android.widget.CheckBox) {
				if (ele.right - ele.paddingRight > ele.left + ele.paddingLeft
						&& ele.bottom - ele.paddingBottom > ele.top + ele.paddingTop) {
					editBoxes.add(ele.nodeid);
				}
			}
		}
	}

	private DirectedGraph<UIElement, DefaultEdge> duplicateWithAbsCoordinate(DirectedGraph<UIElement, DefaultEdge> src,
			HashMap<Integer, UIElement> id2nodemap) {
		DirectedGraph<UIElement, DefaultEdge> dst = new DefaultDirectedGraph<UIElement, DefaultEdge>(DefaultEdge.class);

		UIElement.NODENBR = 0;
		UIElement root = null;
		for (UIElement ele : src.vertexSet()) {
			UIElement newele = ele.clone();
			dst.addVertex(newele);
			id2nodemap.put(ele.nodeid, newele);
			if (ele.nodeid == 1) {
				root = ele;
			}
		}

		Queue<UIElement> queue = new LinkedList<UIElement>();
		queue.offer(root); // root
		while (!queue.isEmpty()) {
			UIElement ele = queue.poll();
			UIElement newele = id2nodemap.get(ele.nodeid);
			Set<DefaultEdge> outedges = src.outgoingEdgesOf(ele);
			for (DefaultEdge outedge : outedges) {

				UIElement succ = src.getEdgeTarget(outedge);
				UIElement newSucc = id2nodemap.get(succ.nodeid);
				// reset the coordinates to be absolute coordinates. the
				// hierarchy order must be ensured here.
				newSucc.left += newele.left;
				newSucc.right += newele.left;
				newSucc.top += newele.top;
				newSucc.bottom += newele.top;
				dst.addEdge(newele, newSucc);
				queue.offer(succ);
			}
		}
		return dst;
	}

	public Set<String> classNamesInLayoutlib(String layoutlib) throws Exception {
		JarInputStream crunchifyJarFile = new JarInputStream(new FileInputStream(layoutlib));
		JarEntry crunchifyJar;
		HashSet<String> classnames = new HashSet<>();
		while (true) {
			crunchifyJar = crunchifyJarFile.getNextJarEntry();
			if (crunchifyJar == null) {
				break;
			}
			if ((crunchifyJar.getName().endsWith(".class"))) {
				String className = crunchifyJar.getName().replaceAll("/", "\\.");
				String myClass = className.substring(0, className.lastIndexOf('.'));
				classnames.add(myClass);
			}
		}
		return classnames;
	}

	public void renderUI(String apkfile, boolean decomplied) throws Exception {
		// String apkfile = "DroidDream.apk";
		resolver = new APKResourceResolver(apkfile);
		resolver.extractARSCAndLayoutFiles();
		renderedLayouts = new HashSet<>();

		layoutNames = new TreeSet<String>();

		if (layoutNames.isEmpty()) {
			Set<String> layouts;
			if (DEBUG)
				layouts = resolver.allLayoutXMLs();// this is mainly used for
													// robust test
			else
				layouts = resolver.getEditableXMLs();
			if (layouts != null) {
				layoutNames.addAll(layouts);
			}
		}

		String apktoolDir = "apk_output";
		File prjFile = new File(apkfile);
		FolderWrapper resFolder = null;
		String dstDir = null;
		try {
			if (prjFile.isFile() && prjFile.canRead()) {
				dstDir = String.format("%s/%s", apktoolDir, prjFile.getName());
				if (decomplied) {
					log.info("Decompiling APK file to %s...", dstDir);
					ApkTool.decodeApk(apkfile, dstDir);
					log.info("Done decompiling APK file to %s...", dstDir);
				}

				resFolder = new FolderWrapper(dstDir + "/res");
			} else {
				throw new IOException("The given APK file is unreachable.");
			}
		} catch (IOException e) {
			if (null != dstDir)
				ApkTool.removeTemp(dstDir);
			log.error("apktoolError for " + apkfile);
			throw e;
		}

		/********************/
		try {
			coorWriter = new FileWriter(dstDir + apkfile + ".coor");
		} catch (Exception e) {
			log.error(e);
		}
		/********************/

		File sdkDir = new File(SDK);
		RenderServiceFactory factory = RenderServiceFactory.create(sdkDir);

		// load the project resources
		ResourceRepository projectRes = new ResourceRepository(resFolder, false /* isFramework */) {

			@Override
			protected ResourceItem createResourceItem(String name) {
				return new ResourceItem(name);
			}
		};

		projectRes.loadResources();

		Set<String> classes = classNamesInLayoutlib(SDK + "/data/layoutlib.jar");
		classes.addAll(classNamesInLayoutlib("lib/support-v4-22.0.0.jar"));
		classes.addAll(classNamesInLayoutlib("lib/appcompat-v7-22.0.0.jar"));
		classes.addAll(classNamesInLayoutlib("lib/support-annotations-22.0.0.jar"));

		// PrintWriter viewclasses = new PrintWriter("allclasses.txt");
		// for (String c : classes) {
		// viewclasses.println(c);
		// }
		// viewclasses.close();

		Pair<String, Boolean> theme;
		FolderConfiguration config = null;
		ResourceResolver resources = null;
		RenderService renderService = null;
		StdOutLogger stdLogger = new StdOutLogger();
		// layout2Hierarchy = new HashMap<String,
		// SparseNumberedGraph<UIElement>>();
		int pass = 1;
		int apiLevel;

		while ((apiLevel = resolver.getNextSpecifiedAPILevel()) != -1) {
			// apiLevel = 22;
			if (config == null) {
				// create the rendering config
				config = RenderServiceFactory.createConfig(480, 800, // size 1
																		// and
																		// 2.
																		// order
																		// doesn't
																		// matter.
																		// Orientation
																		// will
																		// drive
																		// which
																		// is w
																		// and h
						ScreenSize.LARGE, ScreenRatio.LONG, ScreenOrientation.PORTRAIT, Density.MEDIUM,
						TouchScreen.FINGER, KeyboardState.SOFT, Keyboard.QWERTY, NavigationState.EXPOSED,
						Navigation.NONAV, apiLevel); // api level
				// set the LanguageQualifier (use default) in case layoutlib
				// retrieve language resourses other than
				// English
//				config.setLocaleQualifier();
//				config.
			} else {
				config.setVersionQualifier(VersionQualifier.getQualifier(VersionQualifier.getFolderSegment(apiLevel)));
			}
			while ((theme = resolver.getNextAvailableTheme()) != null) {
				String themeName = theme.getFirst();
				boolean isProjectTheme = theme.getSecond().booleanValue();
				// create the resource resolver once for the given config.
				resources = factory.createResourceResolver(config, projectRes, themeName, isProjectTheme);
				// create the render service
				renderService = factory.createService(resources, config, new ProjectCallback(),
						new MyLayoutlibCallBack(resolver, classes, factory.getResTable()));

				log.info("+[Pass: %d] Use API level: %d; Use Theme: %s(%s)", pass++, apiLevel, themeName,
						isProjectTheme);
				renderUiInBatchMode(renderService.setLog(stdLogger), config, resolver, themeName);

				layoutNames.clear();
				if (!failedLayout.isEmpty()) {
					layoutNames.addAll(failedLayout);
				} else {
					log.info("*@<");
					break;
				}
			}
			if (layoutNames.isEmpty()) {
				break;
			}
		}

		log.info("Totally %d layout XMLs are rendered.", renderedLayouts.size());
		if (!failedLayout.isEmpty()) {
			log.info("+[Warning] Below are layout failed to render for %s:", apkfile);
			for (String layout : failedLayout) {

				log.info("+%s", layout);
			}
		}

		// File f = new File(SDK + "/platforms/android-17");

		if (factory == null) {
			System.err.println("Failed to load platform rendering library");
			System.exit(1);
		}

		try {
			resFolder = new FolderWrapper(dstDir + "/res");
		} catch (Exception e) {
			e.printStackTrace();
			;
		}

		// load the project resources

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File f = new File(SDK + "/platforms/android-17");
		RenderServiceFactory factory = RenderServiceFactory.create(f);

		if (factory == null) {
			System.err.println("Failed to load platform rendering library");
			System.exit(1);
		}

		FolderWrapper resFolder = null;
		String dstDir = "testapps/HTTPBreakout";
		try {
			resFolder = new FolderWrapper(dstDir + "/res");
		} catch (Exception e) {
			e.printStackTrace();
		}

		// load the project resources
		ResourceRepository projectRes = new ResourceRepository(resFolder, false /* isFramework */) {

			@Override
			protected ResourceItem createResourceItem(String name) {
				return new ResourceItem(name);
			}
		};

		projectRes.loadResources();

		// create the rendering config
		// FolderConfiguration config = RenderServiceFactory.createConfig(1280,
		// 800, // size
		// // 1
		// // and
		// // 2.
		// // order
		// // doesn't
		// // matter.
		// // Orientation
		// // will
		// // drive
		// // which
		// // is
		// // w
		// // and
		// // h
		// ScreenSize.LARGE, ScreenRatio.LONG, ScreenOrientation.LANDSCAPE,
		// Density.MEDIUM, TouchScreen.FINGER,
		// KeyboardState.SOFT, Keyboard.QWERTY, NavigationState.EXPOSED,
		// Navigation.NONAV, 17); // api
		// // level

		int apilevel = 12;
		FolderConfiguration config = RenderServiceFactory.createConfig(480, 800, // size
																					// 1
																					// and
																					// 2.
																					// order
																					// doesn't
																					// matter.
																					// Orientation
																					// will
																					// drive
																					// which
																					// is
																					// w
																					// and
																					// h
				ScreenSize.LARGE, ScreenRatio.LONG, ScreenOrientation.PORTRAIT, Density.MEDIUM, TouchScreen.FINGER,
				KeyboardState.SOFT, Keyboard.QWERTY, NavigationState.EXPOSED, Navigation.NONAV, apilevel);

		config.setVersionQualifier(VersionQualifier.getQualifier(VersionQualifier.getFolderSegment(apilevel)));

		// create the resource resolver once for the given config.
		ResourceResolver resources = factory.createResourceResolver(config, projectRes, "Theme.Light",
				false /* isProjectTheme */);

		// create the render service
		RenderService renderService = factory.createService(resources, config, new ProjectCallback(),
				new MyLayoutlibCallBack(null, null, null));

		String layoutfile = "main";
		try {
			RenderSession session = renderService.setLog(new StdOutLogger()).setAppInfo("hello", "icon") // optional
					.createRenderSession(layoutfile /* layoutName */);

			// get the status of the render
			Result result = session.getResult();
			if (result.isSuccess() == false) {
				System.err.println(result.getErrorMessage());
				System.exit(1);
			}

			// get the image and save it somewhere.
			BufferedImage image = session.getImage();
			ImageIO.write(image, "png", new File(layoutfile + ".png"));

			// read the views
			displayViewObjects(session.getRootViews());

			return;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.exit(1);
	}

	private static void displayViewObjects(List<ViewInfo> rootViews) {
		for (ViewInfo info : rootViews) {
			displayView(info, "");
		}
	}

	private static void displayView(ViewInfo info, String indent) {
		// display info data
		System.out.println(indent + info.getClassName() + " [" + info.getLeft() + ", " + info.getTop() + ", "
				+ info.getRight() + ", " + info.getBottom() + "]");

		// display the children
		List<ViewInfo> children = info.getChildren();
		if (children != null) {
			indent += "\t";
			for (ViewInfo child : children) {
				displayView(child, indent);
			}
		}
	}
}
