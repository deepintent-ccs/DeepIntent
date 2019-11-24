/*
 * Handlers.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */

package presto.android.gui.clients.testgen;

import com.google.common.collect.Maps;
import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.gui.graph.NInflNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NViewAllocNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.ds.WTGEdge;
import soot.SootClass;

import java.util.Map;

public class Handlers {

  // Specific for OpenManager. Showing if multi-select button is clicked.
  static boolean openmanager_multiselected = false;
  // Specific for BarcodeScanner.
  static Map<Integer, Integer> id2idx = Maps.newHashMap();
  static int nextIdx = -1;
  // Counter to distinguish between windows/widgets in one test case.
  private static int count = 500;
  private static String benchmarkName = Configs.benchmarkName;

  public static void handlePrestoFakeLauncherNodeClass(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    String name = e.getGUIWidget().getClassType().getName();
    if (EventType.implicit_launch_event == type) {
      testCase.append("// Implicit Launch. BenchmarkName: " + benchmarkName);
      if (isBarcodeScanner()) {
        testCase.append("// TODO: choose a barcode");
        testCase.append("clickBarcode(text); // or others");
        HelperDepot.addBarcodeSupport(testCase);
      }
    } else {
      throw new Exception(name + ": event doesn't match: " + type);
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleObject(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.press_key == type) {
      testCase.addImport("android.view.KeyEvent");
      if (isAPV()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "android.app.Dialog")
            && HelperDepot.isAllocatedBy(e.getTargetNode(), "<cx.hell.android.pdfview.OpenFileActivity: void showFindDialog()>")) {
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_SEARCH);");
        } else {
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_VOLUME_UP);");
          testCase.append("//solo.sendKey(KeyEvent.KEYCODE_VOLUME_DOWN);");
          testCase.append("//solo.sendKey(KeyEvent.KEYCODE_DPAD_UP);");
          testCase.append("//solo.sendKey(KeyEvent.KEYCODE_DPAD_DOWN);");
          testCase.append("//... (see more in PagesView#onKey)");
        }
      } else if (isOpenManager()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "android.app.Dialog")) {
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_SEARCH);");
        } else {
          if (HelperDepot.classEquals(e.getTargetNode(), "com.nexes.manager.Main")) {
            testCase.append("solo.clickOnImageButton(1); // to make the BACK button take no effect");
          } else {
            testCase.append("solo.clickOnImageButton(1); // click on HOME button to go to SDCARD directory\n" +
                "solo.clickOnImageButton(0); // click on BACK button to go to root directory");
          }
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_BACK);");
        }
      } else if (isConnectBot()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "org.connectbot.ConsoleActivity")
            && HelperDepot.handlersContainDeclaringClass(e, "org.connectbot.HostListActivity$4")) {
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_ENTER);");
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_ENTER);");
        } else {
          testCase.append("// TODO");
          testCase.append("solo.sendKey(0); // SPECIFY THE KEY");
        }
      } else {
        testCase.append("// TODO");
        String name = "KEY_" + ++count;
        testCase.append("int " + name + " = KeyEvent.KEYCODE_SEARCH; // See http://developer.android.com/reference/android/view/KeyEvent.html");
        testCase.append("solo.sendKey(" + name + ");");
      }
    } else if (EventType.implicit_rotate_event == type) {
      testCase.addImport("android.content.res.Configuration");
      testCase.append("Util.rotateOnce(solo);");
      HelperDepot.addPaiSupport(testCase);
    } else if (EventType.implicit_home_event == type) {
      testCase.append("// Press HOME button");
      testCase.append("Util.homeAndBack(solo);");
      HelperDepot.addPaiSupport(testCase);
    } else if (EventType.implicit_power_event == type) {
      testCase.append("// Press POWER button");
      testCase.append("Util.powerAndBack(solo);");
      HelperDepot.addPaiSupport(testCase);
    } else if (EventType.implicit_back_event == type) {
      Logger.verb("Handlers", "implicit back event");
      if (HelperDepot.isLauncher(e.getTargetNode())) {
        testCase.append("// Leave the app and go back");
        testCase.append("Util.leaveAndBack(solo);");
        return;
      }
      testCase.append("solo.goBack();");
    } else {
      String name = e.getGUIWidget().getClassType().getName();
      throw new Exception(name + ": event doesn't match: " + type);
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleActivity(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NViewAllocNode;
    EventType type = e.getEventType();
    String name = e.getGUIWidget().getClassType().getName();
    // String className = e.getGUIWidget().getClassType().getShortName();
    if (EventType.implicit_on_activity_result == type) {
      testCase.addImport("android.content.Intent");
      String aname = "act_" + ++count;
      testCase.append("Activity " + aname + " = solo.getCurrentActivity();");
      String iname = "intent_" + ++count;
      testCase.append("final Intent " + iname + " = new Intent();");
      String rname = "ReqCode_" + ++count;
      if (isSuperGenPass()) {
        testCase.append(iname + ".setClass(" + aname + ", info.staticfree.SuperGenPass.Preferences.class);");
        testCase.append("int " + rname + " = 200; // corresponding to Super_Gen_Pass.REQUEST_CODE_PREFERENCES");
      } else if (isOpenManager()) {
        testCase.append(iname + ".setClass(" + aname + ", com.nexes.manager.Settings.class);");
        testCase.append("int " + rname + " = 0x10; // corresponding to Main.SETTING_REQ");
        testCase.append(iname + ".putExtra(\"HIDDEN\", false);");
        testCase.append(iname + ".putExtra(\"THUMBNAIL\", true);");
        testCase.append(iname + ".putExtra(\"COLOR\", -1);");
        testCase.append(iname + ".putExtra(\"SORT\", 0);");
        testCase.append(iname + ".putExtra(\"SPACE\", View.VISIBLE);");
        testCase.addImport("android.view.View");
      } else if (isBarcodeScanner()) {
        testCase.append(iname + ".setClass(" + aname + ", com.google.zxing.client.android.history.HistoryActivity.class);");
        testCase.append("int " + rname + " = 0x0000bacc; // corresponding to CaptureActivity.HISTORY_REQUEST_CODE");
      } else {
        testCase.append("// TODO");
        testCase.append("// " + iname + ".setClass(content, class); // MAKE SURE THIS IS CORRECT");
        testCase.append("// MAKE SURE THIS IS THE INTENT EXPECTED");
        testCase.append("// " + iname + ".setAction(...);");
        testCase.append("// " + iname + ".setData(...);");
        testCase.append("// " + iname + ".setType(...);");
        testCase.append("// " + iname + ".setFlags(...);");
        testCase.append("int " + rname + " = 1; // MAKE SURE IT IS THE REQUEST CODE WANTED");
      }
      testCase.append(aname + ".startActivityForResult(" + iname + ", " + rname + ");");
      testCase.append("solo.sleep(5000);");
      if (isSuperGenPass()) {
        testCase.append("assertTrue(\"Activity not match\", solo.waitForActivity(info.staticfree.SuperGenPass.Preferences.class)); // wait for activity to start");
      } else if (isOpenManager()) {
        testCase.append("assertTrue(\"Activity not match\", solo.waitForActivity(com.nexes.manager.Settings.class)); // wait for activity to start");
      } else {
        testCase.append("// assertTrue(\"Activity not match\", solo.waitForActivity(class)); // wait for activity to start");
      }
      testCase.append(aname + " = solo.getCurrentActivity();");
      testCase.append(aname + ".finish(); // finish the activity");
    } else if (EventType.implicit_lifecycle_event == type) {
      // TODO
    } else if (EventType.implicit_on_activity_newIntent == type) {
      testCase.addImport("android.app.Activity");
      testCase.addImport("android.content.Intent");
      if (!isSuperGenPass()) { // benchmarks don't need
        testCase.append("// TODO");
      }
      String aname = "act_" + ++count;
      testCase.append("Activity " + aname + " = solo.getCurrentActivity();\n");
      String iname = "intent_" + ++count;
      testCase.append("Intent " + iname + " = new Intent(" + aname + ", "
          + name + ".class);");
      if (isSuperGenPass()) {
        testCase.append(iname + ".setAction(" + name + ".ACTION_SCAN_SALT);");
        testCase.append("// " + iname + ".setAction(" + name + ".ACTION_GENERATE_SALT);");
      } else {
        testCase.append("// MAKE SURE THIS IS THE INTENT EXPECTED");
        testCase.append("// " + iname + ".setAction(...);");
        testCase.append("// " + iname + ".setData(...);");
        testCase.append("// " + iname + ".setType(...);");
        testCase.append("// " + iname + ".setFlags(...);");
      }
      testCase.append(aname + ".startActivity(" + iname + ");");
    } else {
      throw new Exception(name + ": event doesn't match: " + type);
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleView(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NViewAllocNode;
    EventType type = e.getEventType();
    int resId = HelperDepot.getResId(e.getGUIWidget());
    SootClass classType = e.getGUIWidget().getClassType();
    String className = classType.getShortName();
    if (EventType.click == type || EventType.long_click == type) {
      if (-1 != resId) {
        if (isSuperGenPass()) {
          String idStr = e.getGUIWidget().idNode.getIdName();
          if (idStr.equals("pin_output")) {
            testCase.append("solo.enterText(0, DOMAIN);");
            testCase.append("solo.typeText(1, PASSWORD);");
            testCase.append("solo.clickOnText(\"PIN\");");
          } else if (idStr.equals("password_output")) {
            testCase.append("solo.enterText(0, DOMAIN);");
            testCase.append("solo.typeText(1, PASSWORD);");
            testCase.append("solo.clickOnText(\"PASSWORD\");");
          }
        } else if (isOpenManager()) {
          if (0x7f06003c == resId) { // if multi-select button
            openmanager_multiselected = true;
          }
        }
        testCase.addImport("android.view.View");
        String name = "v_" + ++count;
        String code = "final View " + name + " = solo.getView(";
        int mask = resId & 0x07000000;
        if (0x07000000 == mask) { // application resource
          testCase.addImport(testCase.getPackName() + ".R");
          code += "R.id." + e.getGUIWidget().idNode.getIdName();
        } else if (0x01000000 == mask) { // system resource
          code += "0x" + Integer.toHexString(resId);
        } else {
          throw new Exception(className + ": unknown id.");
        }
        testCase.append(code + ");");
        testCase.append("assertTrue(\"" + className + ": Not Enabled\", " + name + ".isEnabled());");
        if (EventType.click == type)
          testCase.append("solo.clickOnView(" + name + "); ");
        else // long_click
          testCase.append("solo.clickLongOnView(" + name + "); ");
      } else { // no res id
        testCase.addImport("java.util.ArrayList");
        testCase.addImport(classType.getName());
        testCase.append("// TODO");
        String vsname = "VIEWS_" + ++count;
        testCase.append("ArrayList<" + className + "> " + vsname + " = solo.getCurrentViews(" + className + ".class);");
        String vname = "VIEW_" + ++count;
        testCase.append(className + " " + vname + " = " + vsname + ".get(0); // MAKE SURE IT INDEXES THE VIEW EXPECTED\"");
        if (EventType.click == type)
          testCase.append("solo.clickOnView(" + vname + ");");
        else // long_click
          testCase.append("solo.clickLongOnView(" + vname + ");");
      }
    } else if (EventType.touch == type) {
      // TODO
      testCase.append("// TODO: TOUCH THE SCREEN");
    } else if (EventType.item_click == type) {
      // TODO
    } else {
      throw new Exception(className + ": event doesn't match: " + type);
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleListView(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NInflNode
        || e.getGUIWidget() instanceof NViewAllocNode;
    EventType type = e.getEventType();
    if (EventType.item_click == type) {
      // Click a list item
      if (isVuDroid()) { // special case for benchmark VuDroid
        if (HelperDepot.classEquals(e.getTargetNode(), "org.vudroid.pdfdroid.PdfViewerActivity")) {
          if (HelperDepot.handlersContainDeclaringClass(e, "org.vudroid.core.BaseBrowserActivity$1")) {
//            testCase.append("solo.clickInList(PDF_IDX, 0); // click on a PDF file");
            testCase.append("solo.clickOnText(PDF); // click on a PDF file");
          } else if (HelperDepot.handlersContainDeclaringClass(e, "org.vudroid.core.BaseBrowserActivity$4")) {
            testCase.append("// select a PDF file in RECENT list");
//            testCase.append("solo.clickInList(PDF_IDX, 0); // click on a PDF file");
            testCase.append("solo.clickOnText(PDF); // click on a PDF file");
            testCase.append("solo.goBack(); // go back");
            testCase.append("solo.clickOnText(\"Recent\"); // go to recent opened list");
            testCase.append("solo.clickInList(1, 0); // click on the first item");
          }
        } else if (HelperDepot.classEquals(e.getTargetNode(), "org.vudroid.djvudroid.DjvuViewerActivity")) {
          if (HelperDepot.handlersContainDeclaringClass(e, "org.vudroid.core.BaseBrowserActivity$1")) {
//            testCase.append("solo.clickInList(DJVU_IDX, 0); // click on a DJVU file");
            testCase.append("solo.clickOnText(DJVU); // click on a DJVU file");
          } else if (HelperDepot.handlersContainDeclaringClass(e, "org.vudroid.core.BaseBrowserActivity$4")) {
            testCase.append("// select a DJVU file in RECENT list");
//            testCase.append("solo.clickInList(DJVU_IDX, 0); // click on a DJVU file");
            testCase.append("solo.clickOnText(DJVU); // click on a DJVU file");
            testCase.append("solo.goBack(); // go back");
            testCase.append("solo.clickOnText(\"Recent\"); // go to recent opened list");
            testCase.append("solo.clickInList(1, 0); // click on the first item");
          }
        } else { // => MainBrowserActivity, which means clicking on a directory
          testCase.append("solo.clickInList(DIR_IDX, 0); // click on a directory");
          testCase.append("solo.clickOnText(\"sdcard\"); // back to home");
        }
      } else if (isAPV()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "cx.hell.android.pdfview.OpenFileActivity")) {
          testCase.append("solo.clickInList(PDF_IDX, 0);");
        } else { // ChooseFileActivity -> ChooseFileActivity
          testCase.append("solo.clickInList(HOME_IDX, 0); // select a non-PDF item");
        }
      } else if (isOpenManager()) {
        if (HelperDepot.isLauncher(e.getTargetNode())) {
          testCase.append("// cannot be executed, call from another app");
          testCase.append("assertTrue(false);");
          return;
        }
        if (HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")) {
          testCase.append("solo.clickInList(ZIP_IDX, 0); // click on a ZIP file");
        } else if (HelperDepot.classEquals(e.getTargetNode(), "com.nexes.manager.Main")) {
          testCase.append("solo.clickInList(GZ_IDX, 0); // click on a GZ file");
        } else {
          testCase.append("** mishandle ListView");
        }
      } else { // general case
        testCase.append("// TODO");
        String iName = "ITEM_INDEX_" + ++count;
        String lName = "LIST_INDEX_" + ++count;
        testCase.append("int " + iName + " = 1; // MAKE SURE IT INDEXES THE ITEM EXPECTED");
        testCase.append("int " + lName + " = 0; // MAKE SURE IT INDEXES THE LIST EXPECTED");
        testCase.append("solo.clickInList(" + iName + ", " + lName + ");");
      }
    } else if (EventType.item_long_click == type) {
      // TODO
    } else if (EventType.long_click == type) {
      // Long click a list item
      if (isAPV()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "android.view.ContextMenu")) {
          testCase.append("solo.clickLongInList(PDF_IDX, 0); // click on a PDF file");
          testCase.append("//solo.clickLongInList(HOME_IDX, 0); // or click on HOME");
          testCase.append("//solo.clickLongInList(RECENT_IDX, 0); // or click on RECENT FILES");
        } else {
          testCase.append("solo.clickLongInList(DIR_IDX, 0); // click on a directory");
        }
      } else if (isOpenManager()) {
        if (HelperDepot.classEquals(e.getTargetNode(), "com.nexes.manager.Main")) {
          if (!openmanager_multiselected) {
            testCase.append("solo.clickOnImageButton(3); // multi-select first");
            openmanager_multiselected = true;
          }
          testCase.append("solo.clickLongInList(GZ_IDX, 0); // *TODO: click on any item you want");
        } else {
          testCase.append("solo.clickLongInList(DIR_IDX, 0); // *TODO: click on a directory");
          testCase.append("//solo.clickLongInList(PDF_IDX, 0); // or a file");
        }
      } else { // general case
        testCase.append("// TODO");
        String iName = "ITEM_INDEX_" + ++count;
        String lName = "LIST_INDEX_" + ++count;
        testCase.append("int " + iName + " = 1; // MAKE SURE IT INDEXES THE ITEM EXPECTED");
        testCase.append("int " + lName + " = 0; // MAKE SURE IT INDEXES THE LIST EXPECTED");
        testCase.append("solo.clickLongInList(" + iName + ", " + lName + ");");
      }
    } else if (EventType.scroll == type) {
      testCase.append("solo.scrollDown();\nsolo.scrollUp();");
    } else {
      throw new Exception("ListView: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleRadioGroup(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.select == type) {
      testCase.append("// TODO");
      testCase.append("solo.clickOnRadioButton(0); // MAKE SURE IT INDEXES THE ITEM EXPECTED");
    } else {
      throw new Exception("RadioGroup: event doesn't match.");
    }
  }

  public static void handleButton(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NViewAllocNode
        || e.getGUIWidget() instanceof NInflNode;
    EventType type = e.getEventType();
    if (EventType.click == type) {
      int resId = HelperDepot.getResId(e.getGUIWidget());
      String title = HelperDepot.getTitle(e.getGUIWidget());
      if (null != title) {
        if (isVuDroid()) { // special case for benchmark VuDroid
          if (title.equals("Go to Page!")) {
            testCase.append("solo.typeText(0, GotoPageNum);");
          }
        } else if (isOpenManager()) {
          if (0x7f060041 == resId || 0x7f060043 == resId // button is hidden attach, hidden copy
              || 0x7f060042 == resId || 0x7f060044 == resId) { // hidden delete, or hidden move
            if (!openmanager_multiselected) testCase.append("solo.clickOnImageButton(3); // multi-select first");
            openmanager_multiselected = false;
            if (title.equals("Delete")
                && HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")) {
              testCase.append("solo.clickInList(ZIP_IDX, 0); // select a file to delete");
            }
          }
        } else if (benchmarkName.equals("BarcodeScanner")) {
          testCase.append("// *TODO: SCAN A QR CODE NOW");
          testCase.append("solo.sleep(8000);");
        }
        testCase.append("solo.clickOnButton(\"" + title + "\");");
      } else if (-1 != resId) {
        handleTextView(e, testCase);
        return;
      } else {
        // Resource id and title both not available
        if (isAPV()) {
          if (HelperDepot.classEquals(e.getSourceNode(), "android.app.Dialog")
              && HelperDepot.isAllocatedBy(e.getSourceNode(), "<cx.hell.android.pdfview.OpenFileActivity: void showGotoPageDialog()>")) {
            if (HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")
                && HelperDepot.isAllocatedBy(e.getTargetNode(), "<cx.hell.android.pdfview.OpenFileActivity: void errorMessage(java.lang.String)>")) {
              testCase.append("solo.typeText(0, \"9999\");");
            }
            if (HelperDepot.handlersContainMethod(e, "<cx.hell.android.pdfview.OpenFileActivity$13: void onClick(android.view.View)>")) {
              testCase.append("solo.clickOnButton(1); // go to first page");
            } else if (HelperDepot.handlersContainMethod(e, "<cx.hell.android.pdfview.OpenFileActivity$14: void onClick(android.view.View)>")) {
              testCase.append("solo.clickOnButton(2); // go to last page");
            } else if (HelperDepot.handlersContainMethod(e, "<cx.hell.android.pdfview.OpenFileActivity$12: void onClick(android.view.View)>")) {
              testCase.append("solo.clickOnButton(0); // go to page");
            }
          } else if (HelperDepot.handlersContainMethod(e, "<cx.hell.android.pdfview.OpenFileActivity$15: void onClick(android.view.View)>")) {
            testCase.append("solo.clickOnButton(\"Find\");");
          } else if (HelperDepot.classEquals(e.getSourceNode(), "cx.hell.android.pdfview.OpenFileActivity")) {
            testCase.append("solo.sendKey(KeyEvent.KEYCODE_SEARCH);");
            testCase.append("solo.typeText(0, \"a\");");
            testCase.append("solo.clickOnButton(\"Find\");");
            testCase.append("solo.sleep(3000);");
            if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$3")) {
              testCase.append("solo.clickOnButton(\"Prev\");");
              if (HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")
                  && HelperDepot.isAllocatedBy(e.getTargetNode(), "<cx.hell.android.pdfview.OpenFileActivity$Finder$1: void run()>")) {
                testCase.append("assertTrue(\"Dialog not open\", solo.waitForDialogToOpen());");
                return;
              }
            } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$4")) {
              testCase.append("solo.clickOnButton(\"Next\");");
              if (HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")
                  && HelperDepot.isAllocatedBy(e.getTargetNode(), "<cx.hell.android.pdfview.OpenFileActivity$Finder$1: void run()>")) {
                testCase.append("assertTrue(\"Dialog not open\", solo.waitForDialogToOpen());");
                return;
              }
            } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$5")) {
              testCase.append("solo.clickOnButton(\"Hide\");");
            }
          } else {
            testCase.append("// TODO");
            String name = "TITLE_" + ++count;
            testCase.append("String " + name + " = \"title\"; // MAKE SURE IT'S THE BUTTON EXPECTED");
            testCase.append("solo.clickOnButton(" + name + ");");
          }
        } else if (isBarcodeScanner()) {
          NObjectNode button = e.getGUIWidget();
          int idx;
          String name;
          if (id2idx.containsKey(button.id)) {
            idx = id2idx.get(button.id);
          } else {
            idx = ++nextIdx;
            id2idx.put(button.id, idx);
            testCase.addGlobal("int BTN_" + button.id + " = " + idx);
          }
          name = "BTN_" + button.id;
          testCase.append("solo.clickOnButton(" + name + ");");
        } else if (isConnectBot()) {
          if (HelperDepot.classEquals(e.getTargetNode(), "org.connectbot.HelpTopicActivity")
              && HelperDepot.handlersContainDeclaringClass(e, "org.connectbot.HelpActivity$1")) {
            testCase.append("solo.clickOnButton(\"(Hints|Keyboard)\");");
          } else {
            testCase.append("// TODO");
            testCase.append("solo.clickOnButton(\"Some Text\");");
          }
        } else {
          testCase.append("// TODO");
          String name = "BTN_INDEX_" + ++count;
          testCase.append("// int " + name + " = 1; // MAKE SURE IT INDEXES THE BUTTON EXPECTED");
          testCase.append("// solo.clickOnButton(" + name + ");");
          name = "TITLE_" + count;
          testCase.append("String " + name + " = \"title\"; // MAKE SURE IT'S THE BUTTON EXPECTED");
          testCase.append("solo.clickOnButton(" + name + ");");
        }
      }
    } else {
      throw new Exception("Button: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleToggleButton(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    int resId = HelperDepot.getResId(e.getGUIWidget());
    String title = HelperDepot.getTitle(e.getGUIWidget());
    if (EventType.select == type) {
      if (isSuperGenPass()) {
        testCase.append("solo.clickOnText(\"PASSWORD\");");
      }
      if (null != title) {
        testCase.append("solo.clickOnToggleButton(\"" + title + "\");");
      } else if (-1 != resId) {
        testCase.addImport("android.view.View");
        String name = "toggle_btn_" + ++count;
        String code = "final View " + name + " = solo.getView(";
        int mask = resId & 0x07000000;
        if (0x07000000 == mask) { // application resource
          testCase.addImport(testCase.getPackName() + ".R");
          code += "R.id." + e.getGUIWidget().idNode.getIdName();
        } else if (0x01000000 == mask) { // system resource
          code += "0x" + Integer.toHexString(resId);
        } else {
          throw new Exception("ToggleButton: unknown id.");
        }
        testCase.append(code + ");");
        testCase.append("solo.clickOnView(" + name + "); ");
      } else {
        testCase.append("// TODO");
        testCase.append("solo.clickOnToggleButton(\"some text\");");
      }
    } else {
      throw new Exception("ToggleButton: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleImageButton(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NViewAllocNode;
    EventType type = e.getEventType();
    int resId = HelperDepot.getResId(e.getGUIWidget());
    if (EventType.click == type) {
      if (-1 == resId) {
        if (isAPV()) {
          if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$7")) {
            testCase.append("solo.clickOnImageButton(0); // click ZoomDownButton");
          } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$10")) {
            testCase.append("solo.clickOnImageButton(2); // click ZoomUpButton");
          } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$8")) {
            testCase.append("solo.clickOnImageButton(1); // click ZoomWidthButton");
          } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$16")) {
            testCase.append("solo.clickOnImageButton(3); // click menu button");
          } else {
            testCase.append("** mishandle ImageButton");
          }
        } else {
          testCase.append("// TODO");
          String name = "BTN_INDEX_" + ++count;
          testCase.append("int " + name + " = 1; // MAKE SURE IT INDEXES THE BUTTON EXPECTED");
          testCase.append("solo.clickOnImageButton(" + name + ");");
        }
      } else {
        handleView(e, testCase);
        return;
      }
    } else if (EventType.long_click == type) {
      if (isAPV()) {
        testCase.addImport("java.util.ArrayList");
        testCase.addImport("android.widget.ImageButton");
        if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$11")) {
          testCase.append("ArrayList<ImageButton> VIEWS = solo.getCurrentViews(ImageButton.class);\n" +
              "ImageButton VIEW = VIEWS.get(2); // long click ZoomUpButton\"\n" +
              "solo.clickLongOnView(VIEW);");
        } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$9")) {
          testCase.append("ArrayList<ImageButton> VIEWS = solo.getCurrentViews(ImageButton.class);\n" +
              "ImageButton VIEW = VIEWS.get(1); // long click ZoomWidthButton\"\n" +
              "solo.clickLongOnView(VIEW);");
        } else if (HelperDepot.handlersContainDeclaringClass(e, "cx.hell.android.pdfview.OpenFileActivity$6")) {
          testCase.append("ArrayList<ImageButton> VIEWS = solo.getCurrentViews(ImageButton.class);\n" +
              "ImageButton VIEW = VIEWS.get(0); // long click ZoomDownButton\"\n" +
              "solo.clickLongOnView(VIEW);");
        } else if (HelperDepot.handlersContainMethod(e, "<cx.hell.android.pdfview.OpenFileActivity: " +
            "void onCreateContextMenu(android.view.ContextMenu,android.view.View,android.view.ContextMenu$ContextMenuInfo)>")) {
          testCase.append("ArrayList<ImageButton> VIEWS = solo.getCurrentViews(ImageButton.class);\n" +
              "ImageButton VIEW = VIEWS.get(3); // long click menu button\"\n" +
              "solo.clickLongOnView(VIEW);");
        } else {
          handleView(e, testCase);
          return;
        }
      } else {
        handleView(e, testCase);
        return;
      }
    } else {
      throw new Exception("ImageButton: event doesn't match.");
    }

    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleTextView(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NInflNode;
    // int resId = HelperDepot.getResId(e.getGUIWidget());
    String title = HelperDepot.getTitle(e.getGUIWidget());
    EventType type = e.getEventType();
    if (EventType.click == type) {
      if (null != title) {
        testCase.addImport("android.widget.TextView");
        String name = "v_" + ++count;
        String code = "final View " + name + " = findViewByText(\"" + title + "\");\n"
            + "assertTrue(\"TextView: Not Enabled\", " + name + ".isEnabled());";
        HelperDepot.addAllHelpersToCase(testCase);
        testCase.append(code);
        testCase.append("solo.clickOnView(" + name + "); ");
      } else {
        handleView(e, testCase);
        return;
      }
    } else if (EventType.long_click == type) {
      if (isSuperGenPass()) {
        String idStr = e.getGUIWidget().idNode.getIdName();
        if (idStr.equals("pin_output")) {
          testCase.append("solo.enterText(0, DOMAIN);");
          testCase.append("solo.typeText(1, PASSWORD);");
//      testCase.append("solo.clickOnText(\"PIN\");");
        } else if (idStr.equals("password_output")) {
          testCase.append("solo.enterText(0, DOMAIN);");
          testCase.append("solo.typeText(1, PASSWORD);");
//      testCase.append("solo.clickOnText(\"PASSWORD\");");
        }
      }
      handleView(e, testCase);
      return;
    } else {
      throw new Exception("TextView: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleEditText(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NInflNode;
    EventType type = e.getEventType();
    if (EventType.editor_action == type) {
      if (isVuDroid()) {
        testCase.append("solo.typeText(0, GotoPageNum);");
        if (!HelperDepot.classEquals(e.getTargetNode(), "org.vudroid.core.GoToPageDialog")) {
          testCase.addImport("android.view.KeyEvent");
          testCase.append("solo.sendKey(KeyEvent.KEYCODE_ENTER);");
        }
      } else if (isSuperGenPass()) {
        String idStr = e.getGUIWidget().idNode.getIdName();
        if (idStr.equals("password_edit")) {
          testCase.append("solo.enterText(1, PASSWORD);");
        }
      } else {
        testCase.append("// TODO");
        testCase.append("solo.typeText(0, \"some text\");");
        testCase.addImport("android.view.KeyEvent");
        testCase.append("solo.sendKey(KeyEvent.KEYCODE_ENTER);");
      }
    } else if (EventType.enter_text == type) {
      if (isSuperGenPass()) {
        String idStr = e.getGUIWidget().idNode.getIdName();
        if (idStr.contains("domain_edit")) {
          testCase.append("solo.typeText(0, DOMAIN);");
        } else if (idStr.contains("verify")) {
          if (HelperDepot.classEquals(e.getTargetNode(), "android.app.AlertDialog")) {
            testCase.append("// *TODO");
            testCase.append("solo.typeText(0, \"random text\");");
          } else {
            testCase.append("solo.typeText(0, PASSWORD);");
          }
        } else if (idStr.contains("password_edit")) {
          testCase.append("solo.typeText(1, PASSWORD);");
        } else {
          throw new Exception("EditText: SuperGenPass unknown edit text.");
        }
      } else {
        testCase.append("// TODO");
        testCase.append("int INDEX = 0; // MAKE SURE IT INDEXES THE CORRECT TEXT EDIT");
        testCase.append("solo.enterText(INDEX, \"some text\");");
      }
    } else if (EventType.focus_change == type) {
      // TODO:
    } else {
      throw new Exception("TextView: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleMenu(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NOptionsMenuNode;
    EventType type = e.getEventType();
    if (EventType.click == type) {
      if (isSuperGenPass()) {
        testCase.append("solo.enterText(0, DOMAIN);");
        testCase.append("solo.typeText(1, PASSWORD);");
      }
      testCase.addImport("android.view.KeyEvent");
      testCase.append("solo.sendKey(KeyEvent.KEYCODE_MENU);");
    } else {
      throw new Exception("Menu: event doesn't match: " + type);
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleMenuItem(WTGEdge e, Robo.TestCase testCase) throws Exception {
    assert e.getGUIWidget() instanceof NInflNode;
    EventType type = e.getEventType();
    // NObjectNode window = e.getGUIWidget();
    String title = HelperDepot.getTitle(e.getGUIWidget());
    // int resId = HelperDepot.getResId(e.getGUIWidget());
    if (EventType.click == type) {
      testCase.addImport("android.view.View");
      testCase.addImport("android.widget.TextView");
      testCase.addImport("android.view.KeyEvent");
      HelperDepot.addAllHelpersToCase(testCase);
      String code = "";
      if (null != title) {
        testCase.addHelper(HelperDepot.f_handleMenuItemByText);
        if (isVuDroid() && title.equals("Full screen")) { // special case for benchmark
          code += "handleMenuItemByText(\"Full screen (on|off)\");";
        } else if (isVuDroid() && title.equals("Exit")) {
          // mock the exit action
          code += "solo.getCurrentActivity().finish(); // mock EXIT";
        } else if (isSuperGenPass() && title.equals("Copy Generated Password")) {
          code += "solo.clickOnActionBarItem(";
          int resId = HelperDepot.getResId(e.getGUIWidget());
          int mask = resId & 0x07000000;
          if (0x07000000 == mask) { // application resource
            testCase.addImport(testCase.getPackName() + ".R");
            code += "R.id." + e.getGUIWidget().idNode.getIdName();
          } else {
            throw new Exception("MenuItem: unknown id in SuperGenPass");
          }
          code += ");";
        } else if (isOpenManager() && title.equals("Move(Cut) Folder")) {
          code += "handleMenuItemByText(\"Move\\\\(Cut\\\\) Folder\");";
        } else if (isOpenManager() && title.equals("Move(Cut) File")) {
          code += "handleMenuItemByText(\"Move\\\\(Cut\\\\) File\");";
        } else if (isAPV() && title.equals("Clear find")) {
          code += "solo.clickOnText(\"Find…\");\n" +
              "solo.typeText(0, \"a\");\n" +
              "solo.clickOnButton(\"Find\");\n" +
              "solo.sleep(2000);\n" +
              "solo.clickOnImageButton(3);\n" +
              "handleMenuItemByText(\"Clear find\");";
        } else { // general cases
          code += "handleMenuItemByText(\"" + title + "\");";
        }
      } else {
        // Title not available
        if (isSuperGenPass()) {
          if (Hierarchy.v().isContextMenuClass(e.getSourceNode().getWindow().getClassType())
              || HelperDepot.classEquals(e.getTargetNode(), "info.staticfree.SuperGenPass.Super_Gen_Pass")) {
            code += "solo.clickOnText(\"Copy\");";
          } else {
            code += "// TODO\n"
                + "solo.clickOnMenuItem(\"some text\");\n"
                + "// solo.clickOnActionBarItem(resource_id);";
          }
        } else {
          code += "// TODO\n"
              + "solo.clickOnMenuItem(\"some text\");\n"
              + "// solo.clickOnActionBarItem(resource_id);";
        }
      }
      testCase.append(code);
    } else {
      throw new Exception("MenuItem: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleDialog(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.dialog_cancel == type) {
      testCase.append("solo.waitForDialogToClose(); // CANCEL DIALOG");
    } else if (EventType.dialog_dismiss == type) {
      // TODO:
      testCase.append("// Dialog dismiss");
    } else {
      throw new Exception("Dialog: event doesn't match.");
    }
  }

  public static void handleProgressBar(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.drag == type) {
      testCase.append("// TODO");
      testCase.append("int IDX = 0; // MAKE SURE IT'S THE BAR EXPECTED");
      testCase.append("int PROGRESS = 10; // MAKE SURE IT'S THE VALUE EXPECTED");
      testCase.append("solo.setProgressBar(IDX, PROGRESS);");
    } else {
      throw new Exception("SeekBar: event doesn't match.");
    }
  }

  public static void handleSpinner(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.item_selected == type) {
      if (isSuperGenPass()) {
        testCase.append("solo.clickOnText(\"PIN\");");
        testCase.append("solo.pressSpinnerItem(0, PWD_DIGIT_IDX);");
      } else {
        testCase.append("// TODO");
        testCase.append("int SPINNER_IDX = 0; // MAKE SURE IT'S THE BAR EXPECTED");
        testCase.append("int SPINNER_ITEM_IDX = 1; // MAKE SURE IT'S THE VALUE EXPECTED");
        testCase.append("solo.pressSpinnerItem(SPINNER_IDX, SPINNER_ITEM_IDX);");
      }
    } else {
      throw new Exception("Spinner: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  public static void handleCheckBox(WTGEdge e, Robo.TestCase testCase) throws Exception {
    EventType type = e.getEventType();
    if (EventType.select == type) {
      int resId = HelperDepot.getResId(e.getGUIWidget());
      if (-1 != resId) {
        String idStr;
        int mask = resId & 0x07000000;
        if (0x07000000 == mask) { // application resource
          testCase.addImport(testCase.getPackName() + ".R");
          idStr = "R.id." + e.getGUIWidget().idNode.getIdName();
        } else if (0x01000000 == mask) { // system resource
          idStr = "0x" + Integer.toHexString(resId);
        } else {
          throw new Exception("CheckBox: unknown id.");
        }
        testCase.append("View cb = solo.getView(" + idStr + ");");
        testCase.append("solo.clickOnView(cb);");
      } else { // default cases
        testCase.append("// TODO");
        testCase.append("int CheckBoxIdx = 0; // MAKE SURE THIS INDEXES THE CHECK BOX EXPECTED");
        testCase.append("solo.clickOnCheckBox(CheckBoxIdx);");
      }
    } else {
      throw new Exception("CheckBox: event doesn't match.");
    }
    HelperDepot.afterwards(e.getTargetNode(), testCase);
  }

  private static boolean isAPV() {
    return benchmarkName.equals("APV");
  }

  private static boolean isBarcodeScanner() {
    return benchmarkName.equals("BarcodeScanner");
  }

  private static boolean isOpenManager() {
    return benchmarkName.equals("OpenManager");
  }

  private static boolean isSuperGenPass() {
    return benchmarkName.equals("SuperGenPass");
  }

  @SuppressWarnings("unused")
  private static boolean isTippyTipper() {
    return benchmarkName.equals("TippyTipper");
  }

  private static boolean isVuDroid() {
    return benchmarkName.equals("VuDroid");
  }

  private static boolean isConnectBot() {
    return benchmarkName.equals("ConnectBot");
  }
}
