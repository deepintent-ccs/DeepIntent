package presto.android;


import presto.android.xml.PrerunXMLParser;
import soot.Scene;
import soot.SootClass;

/**
 * Created by zero on 12/23/16.
 */
public class PrerunEntrypoint {
  private static PrerunEntrypoint instance;

  private PrerunEntrypoint() {

  }

  public static synchronized PrerunEntrypoint v() {
    if (instance == null) {
      instance = new PrerunEntrypoint();
    }
    return instance;
  }

  public void run() {
    if (Configs.verbose) {
      System.out.println("[DEBUG] Perform Prerun analysis");
    }
    Configs.preRun = true;
    PrerunXMLParser xmlParser = PrerunXMLParser.v();
    for (String str: Configs.onDemandClassSet) {
      //System.out.println(str);
      Scene.v().addBasicClass(str, SootClass.SIGNATURES);
    }
    //Load basic classes
    Scene.v().addBasicClass("android.R$id", SootClass.SIGNATURES);
    Scene.v().addBasicClass("com.android.internal.R$id", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.R$layout", SootClass.SIGNATURES);
    Scene.v().addBasicClass("com.android.internal.R$layout", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.R$menu", SootClass.SIGNATURES);
    Scene.v().addBasicClass("com.android.internal.R$menu", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.R$string", SootClass.SIGNATURES);
    Scene.v().addBasicClass("com.android.internal.R$string", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.app.Activity", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.app.ListActivity", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.widget.TabHost", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.widget.TabHost$TabSpec", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.widget.TabHost$TabContentFactory", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.view.LayoutInflater", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.view.View", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.content.DialogInterface$OnCancelListener", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.content.DialogInterface$OnKeyListener", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.content.DialogInterface$OnShowListener", SootClass.SIGNATURES);
    Scene.v().addBasicClass("android.app.AlertDialog", SootClass.SIGNATURES);

    Configs.preRun = false;
  }
}
