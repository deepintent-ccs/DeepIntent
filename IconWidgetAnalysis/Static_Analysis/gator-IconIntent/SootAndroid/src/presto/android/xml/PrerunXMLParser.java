package presto.android.xml;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.w3c.dom.*;
import presto.android.Configs;
import presto.android.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


/**
 * Created by zero on 12/23/16.
 */
public class PrerunXMLParser {


  private static final boolean debug = false;

  private static PrerunXMLParser theInst;

  public static void resolveGUINameSTR(String guiName) {
    if ("view".equals(guiName)) {
      throw new RuntimeException("It shouldn't happen!!!");
    }
    // TODO: read about mechanism of these tags,
    // and get the real thing in.
    if ("merge".equals(guiName) || "fragment".equals(guiName)) {
      guiName = "LinearLayout";
    }

    else if (guiName.equals("View")) {
      guiName = "android.view.View";
    }

    else if (guiName.equals("WebView")) {
      guiName = "android.webkit.WebView";
    }

    else if (guiName.equals("greendroid.widget.ActionBar")) {
      guiName = "greendroid.widget.GDActionBar";
    }

    // there's in fact a com.facebook.android.LoginButton, but
    // it requires build of some other code, which we may not
    // care. FIXME: change this if later we find it necessary.
    else if (guiName.equals("com.facebook.android.LoginButton")) {
      guiName = "com.facebook.widget.LoginButton";
    }

    // this class is marked @hidden in the platform, so we use its super
    // class instead
    else if (guiName.equals("android.widget.NumberPicker$CustomEditText")) {
      guiName = "android.widget.EditText";
    }
    // DONE with special handling
    if (!guiName.contains(".")) {

      String cls = Configs.widgetMap.get(guiName);
      if (cls == null) {
        if (Configs.verbose) {
          System.out.println("[RESOLVELEVEL] GUI Widget not in the map: " + guiName);
        }
        Configs.onDemandClassSet.add("android.widget." + guiName);
      } else {
        Configs.onDemandClassSet.add(cls);
      }
      return;
    } else {
      Configs.onDemandClassSet.add(guiName);
    }

    // this seems safe, but we really need SootClass.BODIES (TODO)
    // Scene.v().tryLoadClass(guiName, SootClass.HIERARCHY);
  }


  private PrerunXMLParser() {
    doIt();
  }

  public static synchronized PrerunXMLParser v() {
    if (theInst == null) {
      theInst = new PrerunXMLParser();
    }
    return theInst;
  }

  // === implementation details
  private void doIt() {
    readLayout();
    readMenu();
  }

  private static int nonRId = -0x7f040000;

  private void readLayout() {
    for (String resourceLoc : Configs.resourceLocationList) {
      readLayoutRec(resourceLoc + "/");
    }
    readLayoutRec(Configs.sysProj + "/res/");
  }

  private void readLayoutRec(String resRoot) {
    List<String> fileList = retriveXMLFilesFromDirectory(resRoot, "layout");
    for (String fileName : fileList) {
      readLayoutOnSingleFile(fileName);
    }
  }

  private void readLayoutOnSingleFile(String file) {
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

    LinkedList<Node> work = Lists.newLinkedList();
    work.add(rootElement);
    while (!work.isEmpty()) {

      Node node = work.removeFirst();

      NamedNodeMap attrMap = node.getAttributes();
      if (attrMap == null) {
        System.out.println(file + "!!!" + node.getClass() + "!!!"
                + node.toString() + "!!!" + node.getTextContent());
      }

      String guiName = node.getNodeName();
      if ("view".equals(guiName)) {
        guiName = attrMap.getNamedItem("class").getTextContent();
      } else if (guiName.equals("MenuItemView")) {
        // FIXME(tony): this is an "approximation".
        guiName = "android.view.MenuItem";
      }

      //Save
      resolveGUINameSTR(guiName);

      //Look for children
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node newNode = children.item(i);
        String nodeName = newNode.getNodeName();

        if ("#comment".equals(nodeName)) {
          continue;
        }
        if ("#text".equals(nodeName)) {
          // possible for XML files created on a different operating system
          // than the one our analysis is run on
          continue;
        }
        if (nodeName.equals("requestFocus")) {
          continue;
        }

        if (newNode.getNodeName().equals("include")) {
          continue;
        }
        work.add(newNode);
      }
    }
  }

  private List<String> retriveXMLFilesFromDirectory(String baseDirName, String subDirName) {
    List<String> fileList = Lists.newArrayList();
    File folder = new File(baseDirName);
    if (folder.isDirectory()) {
      for (File curFile :folder.listFiles()) {
        if (    curFile.isDirectory()
                && curFile.getName().length() >= subDirName.length()
                && curFile.getName().startsWith(subDirName)) {
          for (File curSubFile : curFile.listFiles()) {
            if (curSubFile.getName().endsWith(".xml")) {
              fileList.add(curSubFile.getAbsolutePath());
            }
          }
        }
      }
    }
    return fileList;
  }

  // --- read menu*/*.xml
  private void readMenu() {
    for (String resourceLoc : Configs.resourceLocationList) {
      readMenuRec(resourceLoc + "/");
    }
    readMenuRec(Configs.sysProj + "/res/");
  }

  private void readMenuRec(String dirName) {
    List<String> fileNames = retriveXMLFilesFromDirectory(dirName, "menu");
    for (String curFile : fileNames) {
      readMenuOnSingleFile(curFile);
    }
  }

  private void readMenuOnSingleFile(String file) {
    Document doc;
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      doc = dBuilder.parse(file);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    LinkedList<Node> worklist = Lists.newLinkedList();
    worklist.add(doc.getDocumentElement());
    while (!worklist.isEmpty()) {
      Node node = worklist.removeFirst();
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
        //throw new RuntimeException("Unhandled menu tag " + guiName);
      }
      resolveGUINameSTR(guiName);
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node newNode = children.item(i);
        String nodeName = newNode.getNodeName();
        if ("#comment".equals(nodeName)) {
          continue;
        }
        if ("#text".equals(nodeName)) {
          // possible for XML files created on a different operating system
          // than the one our analysis is run on
          continue;
        }
        worklist.add(newNode);
      }
    }
  }
}

class HelperInner {
  // These are declared in the manifest, but no corresponding .java/.class
  // files are available. Most likely, code was deleted while manifest has
  // not been updated.
  static Set<String> astridMissingActivities = Sets.newHashSet(
          "com.todoroo.astrid.tags.reusable.FeaturedListActivity",
          "com.todoroo.astrid.actfm.TagViewWrapperActivity",
          "com.todoroo.astrid.actfm.TagCreateActivity",
          "com.todoroo.astrid.gtasks.auth.GtasksAuthTokenProvider",
          "com.todoroo.astrid.reminders.NotificationWrapperActivity");
  static Set<String> nprMissingActivities = Sets.newHashSet(
          "com.crittercism.NotificationActivity");

  public static String getClassName(String classNameFromXml, String appPkg) {
    if ('.' == classNameFromXml.charAt(0)) {
      classNameFromXml = appPkg + classNameFromXml;
    }
    if (!classNameFromXml.contains(".")) {
      classNameFromXml = appPkg + "." + classNameFromXml;
    }
    if (Configs.benchmarkName.equals("Astrid")
            && astridMissingActivities.contains(classNameFromXml)) {
      return null;
    }
    if (Configs.benchmarkName.equals("NPR")
            && nprMissingActivities.contains(classNameFromXml)) {
      return null;
    }
    return classNameFromXml;
  }
}
