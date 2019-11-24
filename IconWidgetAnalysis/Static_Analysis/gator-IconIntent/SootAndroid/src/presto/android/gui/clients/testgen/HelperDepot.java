/*
 * HelperDepot.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */

package presto.android.gui.clients.testgen;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import presto.android.Hierarchy;
import presto.android.gui.PropertyManager;
import presto.android.gui.graph.*;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Set;

/**
 * Helpers' code warehouse.
 */
public class HelperDepot {

  /**
   * Default Velocity template.
   */
  public final static String template = "/*\n"
      + " *\n"
      + " * This file is automatically created by Gator.\n"
      + " *\n"
      + " */\n\n"
      + "package ${package}.tests;\n\n"
      + "import android.test.ActivityInstrumentationTestCase2;\n"
      + "import android.util.Log;\n"
      + "import com.robotium.solo.Solo;\n"
      + "#foreach( ${import} in ${import_list} )\n"
      + "import ${import};\n"
      + "#end\n"
      + "import ${activity_whole_path};\n\n"
      + "public class $classname extends ActivityInstrumentationTestCase2<${activity}> {\n\n"
      + "  private Solo solo;\n"
      + "  private final static String TAG = \"Gator.TestGenClient\";\n\n"
      + "#foreach( ${global} in ${global_list} )\n"
      + "  private final ${global};\n"
      + "#end\n\n"
      + "  public $classname() {\n"
      + "    super(${init});\n"
      + "  }\n\n"
      + "  @Override\n"
      + "  public void setUp() throws Exception {\n"
      + "    solo = new Solo(getInstrumentation(), getActivity());\n"
      + "    solo.unlockScreen();\n"
      + "#foreach( ${setup} in ${setup_list} )"
      + "    ${setup}\n"
      + "#end"
      + "  }\n\n"
      + "  @Override\n"
      + "  public void tearDown() throws Exception {\n"
      + "    solo.finishOpenedActivities();\n"
      + "  }\n\n\n"
      + "#set( $count = 1 )\n"
      + "#set( $String = \"\")\n"
      + "#foreach( ${test} in ${test_list} )\n"
      + "  public void test${methodname}$String.format(\"%03d\", ${count})() throws Exception {\n"
      + "${test}"
      + "  }\n\n"
      + "#set( $count = $count + 1 )\n"
      + "#end\n"
      + "  /*\n"
      + "   * ============================== Helpers ==============================\n"
      + "   */\n"
      + "#foreach( ${helper} in ${helper_list} )\n"
      + "${helper}\n"
      + "#end"
      + "#foreach( ${helper_class} in ${helper_classes} )\n"
      + "${helper_class}\n"
      + "#end"
      + "}\n";
  /**
   * Helper functions for ActionBar.
   */
  public final static String allHelpers = "  public View getActionBarView() {\n"
      + "    //solo.sleep(2000);\n"
      + "    ArrayList<View> alViews = solo.getCurrentViews();\n"
      + "    for (View curView :alViews) {\n"
      + "      String className = curView.getClass().getName();\n"
      + "      if (className.endsWith(\"ActionBarContainer\")) {\n"
      + "        return curView;\n"
      + "      }\n"
      + "    }\n"
      + "    return null;\n"
      + "  }\n\n"
      + "  private ArrayList<View> getActionBarItemsWithMenuButton() {\n"
      + "    ViewGroup ActionBarContainer = (ViewGroup) this.getActionBarView();\n"
      + "    ArrayList<View> ret = new ArrayList<View>();\n"
      + "    ViewGroup ActionMenuView = (ViewGroup) recursiveFindActionMenuView(ActionBarContainer);\n"
      + "    if (ActionMenuView == null) {\n"
      + "      //The ActionBar is empty. Should not happen\n"
      + "      return null;\n"
      + "    }\n"
      + "    for (int i = 0; i < ActionMenuView.getChildCount(); i++) {\n"
      + "      View curView = ActionMenuView.getChildAt(i);\n"
      + "      ret.add(curView);\n"
      + "    }\n"
      + "    return ret;\n"
      + "  }\n\n"
      + "  public ArrayList<View> getActionBarItems() {\n"
      + "    ArrayList<View> ActionBarItems = getActionBarItemsWithMenuButton();\n"
      + "    if (ActionBarItems == null) {\n"
      + "      return null;\n"
      + "    }\n"
      + "    for (int i = 0; i < ActionBarItems.size(); i++) {\n"
      + "      View curView = ActionBarItems.get(i);\n"
      + "      String className = curView.getClass().getName();\n"
      + "      if (className.endsWith(\"OverflowMenuButton\")) {\n"
      + "        ActionBarItems.remove(i);\n"
      + "        return ActionBarItems;\n"
      + "      }\n"
      + "    }\n"
      + "    return ActionBarItems;\n"
      + "  }\n\n"
      + "  public View getActionBarMenuButton() {\n"
      + "    ArrayList<View> ActionBarItems = getActionBarItemsWithMenuButton();\n"
      + "    if (ActionBarItems == null) {\n"
      + "      return null;\n"
      + "    }\n"
      + "    for (int i = 0; i < ActionBarItems.size(); i++) {\n"
      + "      View curView = ActionBarItems.get(i);\n"
      + "      String className = curView.getClass().getName();\n"
      + "      if (className.endsWith(\"OverflowMenuButton\")) {\n"
      + "        return curView;\n"
      + "      }\n"
      + "    }\n"
      + "    return null;\n"
      + "  }\n\n"
      + "  public View getActionBarItem(int index) {\n"
      + "    ArrayList<View> ActionBarItems = getActionBarItems();\n"
      + "    if (ActionBarItems == null) {\n"
      + "      //There is no ActionBar\n"
      + "      return null;\n"
      + "    }\n"
      + "    if (index < ActionBarItems.size()) {\n"
      + "      return ActionBarItems.get(index);\n"
      + "    } else {\n"
      + "      //Out of range\n"
      + "      return null;\n"
      + "    }\n"
      + "  }\n\n"
      + "  private View recursiveFindActionMenuView(View entryPoint) {\n"
      + "    String curClassName = \"\";\n"
      + "    curClassName = entryPoint.getClass().getName();\n"
      + "    if (curClassName.endsWith(\"ActionMenuView\")) {\n"
      + "      return entryPoint;" + "\n" + "    }" + "\n"
      + "    //entryPoint is not an ActionMenuView" + "\n"
      + "    if (entryPoint instanceof ViewGroup) {" + "\n"
      + "      ViewGroup vgEntry = (ViewGroup)entryPoint;" + "\n"
      + "      for ( int i = 0; i<vgEntry.getChildCount(); i ++) {" + "\n"
      + "        View curView = vgEntry.getChildAt(i);" + "\n"
      + "        View retView = recursiveFindActionMenuView(curView);" + "\n\n"
      + "        if (retView != null) {" + "\n"
      + "          //ActionMenuView was found" + "\n"
      + "          return retView;" + "\n" + "        }" + "\n" + "      }\n"
      + "      //Still not found" + "\n" + "      return null;" + "\n"
      + "    } else {" + "\n" + "      return null;" + "\n" + "    }" + "\n"
      + "  }" + "\n\n"
      + "  public View getActionBarMenuItem(int index) {" + "\n"
      + "    View ret = null;" + "\n"
      + "    ArrayList<View> MenuItems = getActionBarMenuItems();" + "\n"
      + "    if (MenuItems != null && index < MenuItems.size()) {" + "\n"
      + "      ret = MenuItems.get(index);" + "\n" + "    }" + "\n"
      + "    return ret;" + "\n" + "  }" + "\n\n"
      + "  public ArrayList<View> getActionBarMenuItems() {\n"
      + "    ArrayList<View> MenuItems = new ArrayList<View>();\n"
      + "    ArrayList<View> curViews = solo.getCurrentViews();\n\n"
      + "    for (int i = 0; i < curViews.size(); i++) {\n"
      + "      View itemView = curViews.get(i);\n"
      + "      String className = itemView.getClass().getName();\n"
      + "      if (className.endsWith(\"ListMenuItemView\")) {\n"
      + "        MenuItems.add(itemView);" + "\n" + "      }" + "\n"
      + "    }\n" + "    return MenuItems;" + "\n" + "  }" + "\n";
  /**
   * Find view by text as regex.
   */
  public final static String f_findViewByText = "  public View findViewByText(String text) {\n"
      + "    boolean shown = solo.waitForText(text);\n"
      + "    if (!shown) return null;\n"
      + "    ArrayList<View> views = solo.getCurrentViews();\n"
      + "    for (View view : views) {\n"
      + "      if (view instanceof TextView) {\n"
      + "        TextView textView = (TextView) view;\n"
      + "        String textOnView = textView.getText().toString();\n"
      + "        if (textOnView.matches(text)) {\n"
      + "          Log.v(TAG, \"Find View (By Text \" + textOnView + \"): \" + view);\n"
      + "          return view;" + "\n" + "        }" + "\n" + "      }" + "\n"
      + "    }" + "\n" + "    return null;" + "\n" + "  }" + "\n";

  public final static String f_handleMenuItemByText = "  @SuppressWarnings(\"unchecked\")\n" +
      "  public void handleMenuItemByText(String title) {\n" +
      "    View v = findViewByText(title);\n" +
      "    if (null != v) {\n" +
      "      // Menu item in option menu (or on action bar if no menu poped)\n" +
      "      // assertTrue(\"MenuItem: Not Enabled.\", v.isEnabled());\n" +
      "      solo.clickOnText(title);\n" +
      "    } else {\n" +
      "      boolean hasMore = solo.searchText(\"More\");\n" +
      "      if (hasMore) {\n" +
      "        solo.clickOnMenuItem(\"More\");\n" +
      "        handleMenuItemByText(title);\n" +
      "        return;\n" +
      "      }\n" +
      "      // Menu item on action bar\n" +
      "      Class<? extends View> cls = null;\n" +
      "      try {\n" +
      "        cls = (Class<? extends View>) Class.forName(\"com.android.internal.view.menu.MenuView$ItemView\");\n" +
      "      } catch (ClassNotFoundException e) {\n" +
      "        e.printStackTrace();\n" +
      "      }\n" +
      "      ArrayList<? extends View> views = solo.getCurrentViews(cls);\n" +
      "      if (!views.isEmpty()) {\n" +
      "        solo.sendKey(KeyEvent.KEYCODE_MENU); // Hide option menu\n" +
      "        assertTrue(\"Menu Not Closed\", solo.waitForDialogToClose());\n" +
      "      }\n" +
      "      View actionBarView = getActionBarView();\n" +
      "      assertNotNull(\"Action Bar Not Found\", actionBarView);\n" +
      "      boolean onActionBar = false;\n" +
      "      for (View abv : getActionBarItems()) {\n" +
      "        for (View iv : solo.getViews(abv)) {\n" +
      "          if (iv instanceof TextView) {\n" +
      "            if (((TextView) iv).getText().toString().matches(title)) {\n" +
      "              onActionBar = true;\n" +
      "              assertTrue(\"MenuItem: Not Clickable.\", iv.isClickable());\n" +
      "              solo.clickOnView(iv);\n" +
      "              break;\n" +
      "            }\n" +
      "          }\n" +
      "        }\n" +
      "        if (onActionBar) break;\n" +
      "      }\n" +
      "      if (!onActionBar) {\n" +
      "        // In action bar menu\n" +
      "        boolean found = false;\n" +
      "        View abMenuButton = getActionBarMenuButton();\n" +
      "        assertNotNull(\"Action Bar Menu Button Not Found\", abMenuButton);\n" +
      "        solo.clickOnView(abMenuButton);\n" +
      "        assertTrue(\"Action Bar Not Open\", solo.waitForDialogToOpen());\n" +
      "        ArrayList<View> acBarMIs = getActionBarMenuItems();\n" +
      "        for (View item : acBarMIs) {\n" +
      "          for (View iv : solo.getViews(item)) {\n" +
      "            if (iv instanceof TextView) {\n" +
      "              if (((TextView) iv).getText().toString().matches(title)) {\n" +
      "                found = true;\n" +
      "                assertTrue(\"MenuItem: Not Clickable.\", iv.isClickable());\n" +
      "                solo.clickOnView(iv);\n" +
      "                break;\n" +
      "              }\n" +
      "            }\n" +
      "          }\n" +
      "          if (found) break;\n" +
      "        }\n" +
      "        assertTrue(\"MenuItem: not found.\", found);\n" +
      "      }\n" +
      "    }\n" +
      "  }\n";

  private final static String f_assertActivity = "  // Assert activity\n" +
      "  public void assertActivity(Class<? extends Activity> cls) {\n" +
      "    solo.sleep(2000);\n" +
      "    assertFalse(\"Dialog or Menu shows up.\", solo.waitForDialogToOpen(2000));\n" +
      "    assertTrue(\"Activity does not match.\", solo.waitForActivity(cls));\n" +
      "  }\n";

  private final static String f_assertMenu = "  // Assert menu\n" +
      "  @SuppressWarnings(\"unchecked\")\n" +
      "  public void assertMenu() {\n" +
      "    solo.sleep(2000);\n" +
      "    Class<? extends View> cls = null;\n" +
      "    try {\n" +
      "      cls = (Class<? extends View>) Class.forName(\"com.android.internal.view.menu.MenuView$ItemView\");\n" +
      "    } catch (ClassNotFoundException e) {\n" +
      "      e.printStackTrace();\n" +
      "    }\n" +
      "    ArrayList<? extends View> views = solo.getCurrentViews(cls);\n" +
      "    assertTrue(\"Menu not open.\", !views.isEmpty());\n" +
      "  }\n";

  private final static String f_assertDialog = "  // Assert dialog\n" +
      "  @SuppressWarnings(\"unchecked\")\n" +
      "  public void assertDialog() {\n" +
      "    solo.sleep(2000);\n" +
      "    assertTrue(\"Dialog not open\", solo.waitForDialogToOpen());\n" +
      "    Class<? extends View> cls = null;\n" +
      "    try {\n" +
      "      cls = (Class<? extends View>) Class.forName(\"com.android.internal.view.menu.MenuView$ItemView\");\n" +
      "    } catch (ClassNotFoundException e) {\n" +
      "      e.printStackTrace();\n" +
      "    }\n" +
      "    ArrayList<? extends View> views = solo.getCurrentViews(cls);\n" +
      "    assertTrue(\"Menu not open.\", views.isEmpty());\n" +
      "  }\n";

  private final static String f_pai_ase15 = "  // PRESTO Android Infrastructure \n" +
      "  static class CommandExecutor {\n" +
      "    public static final String EXE_TAG = \"Xewr6chA\";\n" +
      "    public static final String REPLAY = \"REPLAY\";\n" +
      "\n" +
      "    static void execute(Solo solo, String cmd, int delay) {\n" +
      "      Log.i(EXE_TAG, cmd);\n" +
      "      solo.sleep(delay);\n" +
      "    }\n" +
      "\n" +
      "    static void execute(String cmd) {\n" +
      "      Log.i(EXE_TAG, cmd);\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  static class Util {\n" +
      "    public static final String HOME_EVENT = \"/data/presto/home_event\";\n" +
      "    public static final int HOME_DELAY = 4000;\n" +
      "    public static final String POWER_EVENT = \"/data/presto/power_event\";\n" +
      "    public static final int POWER_DELAY = 4000;\n" +
      "    public static final String ROTATE1_EVENT = \"/data/presto/rotate1_event\";\n" +
      "    public static final String ROTATE2_EVENT = \"/data/presto/rotate2_event\";\n" +
      "    public static final int ROTATE_DELAY = 4000;\n" +
      "    public static int rotateDelay = 1000;\n" +
      "    public static int homeDelay = 2000;\n" +
      "    public static int powerDelay = 1000;\n" +
      "    // At first activity or any activity with similar nature, press\n" +
      "    // BACK to leave the app, long click the HOME button, and re-enter\n" +
      "    // the app.\n" +
      "    public static final String LEAVE_EVENT = \"/data/presto/leave_event\";\n" +
      "    public static final int LEAVE_DELAY = 4000;\n" +
      "    public static final String SWITCH_APP_EVENT = \"/data/presto/switch_app_event\";\n" +
      "\n" +
      "    public static void replay(Solo solo, String event, int delay) {\n" +
      "      String cmd = CommandExecutor.REPLAY + \" \" + event;\n" +
      "      CommandExecutor.execute(solo, cmd, delay);\n" +
      "    }\n" +
      "\n" +
      "    public static void rotate(Solo solo) {\n" +
      "      solo.setActivityOrientation(Solo.LANDSCAPE);\n" +
      "      solo.sleep(rotateDelay);\n" +
      "      solo.setActivityOrientation(Solo.PORTRAIT);\n" +
      "      solo.sleep(rotateDelay);\n" +
      "    }\n" +
      "\n" +
      "    public static void rotateOnce(Solo solo) {\n" +
      "      int CUR_ORIENTATION = solo.getCurrentActivity().getResources().getConfiguration().orientation;\n" +
      "      if (CUR_ORIENTATION == Configuration.ORIENTATION_LANDSCAPE) {\n" +
      "        solo.setActivityOrientation(Solo.PORTRAIT);\n" +
      "      } else if (CUR_ORIENTATION == Configuration.ORIENTATION_PORTRAIT) {\n" +
      "        solo.setActivityOrientation(Solo.LANDSCAPE);\n" +
      "      }\n" +
      "    }\n" +
      "\n" +
      "    // a record-replay based rotation\n" +
      "    public static void rrRotate(Solo solo) {\n" +
      "      replay(solo, ROTATE1_EVENT, ROTATE_DELAY);\n" +
      "      solo.sleep(rotateDelay);\n" +
      "      replay(solo, ROTATE2_EVENT, ROTATE_DELAY);\n" +
      "      solo.sleep(rotateDelay);\n" +
      "    }\n" +
      "\n" +
      "    public static void homeAndBack(Solo solo) {\n" +
      "      replay(solo, HOME_EVENT, HOME_DELAY);\n" +
      "    }\n" +
      "\n" +
      "    public static void powerAndBack(Solo solo) {\n" +
      "      replay(solo, POWER_EVENT, POWER_DELAY);\n" +
      "    }\n" +
      "\n" +
      "    public static void leaveAndBack(Solo solo) {\n" +
      "      replay(solo, LEAVE_EVENT, LEAVE_DELAY);\n" +
      "    }\n" +
      "\n" +
      "    public static void ent() {\n" +
      "      CommandExecutor.execute(\"EXIT\");\n" +
      "    }\n" +
      "  }\n";

  private static String f_scanBarcode = "    // Choose a barcode to click\n" +
      "    void clickBarcode(int line) {\n" +
      "      Activity act = solo.getCurrentActivity();\n" +
      "      final Intent intent = new Intent();\n" +
      "      intent.setClass(act, com.google.zxing.client.android.history.HistoryActivity.class);\n" +
      "      int ReqCode = 0x0000bacc; // corresponding to CaptureActivity.HISTORY_REQUEST_CODE\n" +
      "      act.startActivityForResult(intent, ReqCode);" +
      "      switch(line) {\n" +
      "      case text:\n" +
      "        solo.clickOnText(\"Hello World!*\");\n" +
      "        break;\n" +
      "      case product:\n" +
      "        solo.clickOnText(\"784672659826*\");\n" +
      "        break;\n" +
      "      case wifi:\n" +
      "        solo.clickOnText(\"WIFI*\");\n" +
      "        break;\n" +
      "      case uri:\n" +
      "        solo.clickOnText(\"http://google.com*\");\n" +
      "        break;\n" +
      "      case addressbook:\n" +
      "        solo.clickOnText(\"BEGIN:VCARD*\");\n" +
      "        break;\n" +
      "      case email:\n" +
      "        solo.clickOnText(\"mailto*\");\n" +
      "        break;\n" +
      "      case isbn:\n" +
      "        solo.clickOnText(\"9781234567897*\");\n" +
      "        break;\n" +
      "      case geo:\n" +
      "        solo.clickOnText(\"geo*\");\n" +
      "        break;\n" +
      "      case sms:\n" +
      "        solo.clickOnText(\"smsto*\");\n" +
      "        break;\n" +
      "      case tel:\n" +
      "        solo.clickOnText(\"tel*\");\n" +
      "        break;\n" +
      "      case calendar:\n" +
      "        solo.clickOnText(\"BEGIN:VEVENT*\");\n" +
      "        break;\n" +
      "      }\n" +
      "    }\n";

  @SuppressWarnings("unused")
  private static int counter = 100;

  public static void addPaiSupport(Robo.TestCase testCase) {
    testCase.addHelperClass(f_pai_ase15);
  }

  public static void addBarcodeSupport(Robo.TestCase testCase) {
    testCase.addHelper(f_scanBarcode);
  }

  public static Robo.TestCase afterwards(WTGNode tgt, Robo.TestCase testCase) {
    if (Hierarchy.v().isActivityClass(tgt.getWindow().getClassType())) {
//      String code = "assertFalse(\"Dialog or Menu, instead of activity, shows up.\", solo.waitForDialogToOpen());\n"
//          + "assertTrue(\"Activity does not match.\", solo.waitForActivity(" + tgt.getWindow().getClassType().getName() + ".class));\n";
      String code = "assertActivity(" + tgt.getWindow().getClassType().getName() + ".class);";
      testCase.addHelper(f_assertActivity);
//      testCase.addImport(tgt.getWindow().getClassType().getName());
      testCase.addImport("android.app.Activity");
      testCase.append(code);
      return testCase;
    } else if (Hierarchy.v().isDialogClass(tgt.getWindow().getClassType())) {
//      String clsName = "cls_" + ++counter;
//      String code = "assertTrue(\"Dialog not open\", solo.waitForDialogToOpen());\n";
//      code += "@SuppressWarnings(\"unchecked\")\n" +
//          "Class<? extends View> " + clsName + " = (Class<? extends View>) Class.forName(\"com.android.internal.view.menu.ListMenuItemView\");\n" +
//          "assertFalse(\"Menu not open.\", solo.waitForView(" + clsName + "));\n";
      String code = "assertDialog();";
      testCase.addImport("android.view.View");
      testCase.addImport("java.util.ArrayList");
      testCase.addHelper(f_assertDialog);
      testCase.append(code);
      return testCase;
    } else if (Hierarchy.v().isMenuClass(tgt.getWindow().getClassType())) {
//      String clsName = "cls_" + ++counter;
//      String code = "@SuppressWarnings(\"unchecked\")\n" +
//          "Class<? extends View> " + clsName + " = (Class<? extends View>) Class.forName(\"com.android.internal.view.menu.ListMenuItemView\");\n" +
//          "assertTrue(\"Menu not open.\", solo.waitForView(" + clsName + "));\n";
      String code = "assertMenu();";
      testCase.addHelper(f_assertMenu);
      testCase.addImport("android.view.View");
      testCase.addImport("java.util.ArrayList");
      testCase.append(code);
      return testCase;
    } else if (tgt.getWindow().getClassType().getName().endsWith("PrestoFakeLauncherNodeClass")) {
      testCase.append("// exit the application");
      return testCase;
    }
    throw new RuntimeException("No such window: " + tgt.getWindow().getClassType());
  }

  public static void addAllHelpersToCase(Robo.TestCase tc) {
    tc.addImport("android.view.View");
    tc.addImport("android.view.KeyEvent");
    tc.addImport("java.util.ArrayList");
    tc.addImport("android.view.ViewGroup");
    tc.addImport("android.widget.TextView");
    tc.addHelper(f_findViewByText);
    tc.addHelper(HelperDepot.allHelpers);
  }

  /**
   * @param n
   * @return the resource id of a NNode
   */
  public static int getResId(NNode n) {
    NIdNode idNode = n.idNode;
    if (idNode == null // no id
        || idNode instanceof NAnonymousIdNode) { // or a non resource id
      return -1;
    }
    Integer idValue = idNode.getIdValue();
    return null == idValue ? -1 : idValue.intValue();
  }

  /**
   * @param n
   * @return title of a NObjectNode instance
   */
  public static String getTitle(NObjectNode n) {
    Set<String> allTitles = PropertyManager.v().getTextsOrTitlesOfView(n);
    if (1 == allTitles.size()) {
      return Lists.newArrayList(allTitles).get(0);
    }
    return null;
  }

  public static boolean handlersContainDeclaringClass(WTGEdge e, String name) {
    for (SootMethod m : e.getEventHandlers())
      if (classEquals(m.getDeclaringClass(), name)) return true;
    return false;
  }

  public static boolean handlersContainMethod(WTGEdge e, String signature) {
    for (SootMethod m : e.getEventHandlers())
      if (methodEquals(m, signature)) return true;
    return false;
  }

  private static boolean classEquals(SootClass c, String name) {
    return c.equals(Scene.v().getSootClass(name));
  }

  public static boolean classEquals(WTGNode n, String name) {
    return classEquals(n.getWindow().getClassType(), name);
  }

  public static boolean isLauncher(WTGNode n) {
    if (n.getWindow() instanceof NLauncherNode) return true;
    return false;
  }

  public static boolean isAllocatedBy(WTGNode n, String signature) {
    Preconditions.checkArgument(n.getWindow() instanceof NDialogNode);
    return methodEquals(((NDialogNode) n.getWindow()).allocMethod, signature);
  }

  private static boolean methodEquals(SootMethod m, String signature) {
    return m.equals(Scene.v().getMethod(signature));
  }
}
