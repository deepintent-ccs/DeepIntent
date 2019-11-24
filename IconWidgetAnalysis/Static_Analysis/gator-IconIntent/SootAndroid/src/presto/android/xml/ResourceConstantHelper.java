package presto.android.xml;

import com.google.common.collect.Maps;
import presto.android.Configs;
import presto.android.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * Created by zero on 5/3/16.
 */
public class ResourceConstantHelper {
  public static final String INTERNAL_R_FILENAME = "com_android_internal_R";
  public static String getCompatibleAPILevelFile(int iLevel) {
    String GatorRootPath = System.getenv("GatorRoot");
    if (GatorRootPath == null || GatorRootPath.isEmpty()) {
      Logger.verb("ERROR", "GatorRoot environment variable not defined");
      //TODO: If Environment var not set, use current working directory plus const/filename
      System.exit(-1);
    }
    //Locate the const dir
    String constDir = GatorRootPath + "/SootAndroid/scripts/consts/";
    File constDirFile = new File(constDir);
    if (!constDirFile.exists() || (!constDirFile.isDirectory())) {
      Logger.verb("ERROR", "SootAndroid/consts/ directory not exist");
      System.exit(-1);
    }

    Map<Integer, File> platformDirs = Maps.newHashMap();

    //Scan available const
    for (File curFile : constDirFile.listFiles()) {
      if (!curFile.isDirectory()) {
        continue;
      }
      //It is a directory
      if (curFile.getName().startsWith("android-")) {
        String dirNum = curFile.getName().substring(8);
        Integer iDirNum = Integer.parseInt(dirNum);
        if (iDirNum != null && isInternalConstAvailable(curFile)) {
          platformDirs.put(iDirNum, curFile);
        }
      }
    }

    //Match current API Level
    for (; iLevel >= 10; iLevel--) {
      if (platformDirs.containsKey(iLevel)) {
        //There is an exact match
        File matchedDir = platformDirs.get(iLevel);
        return matchedDir.getAbsolutePath() + "/" + INTERNAL_R_FILENAME;
      }
    }

    //Not match, return default.
    return constDir + INTERNAL_R_FILENAME;
  }

  private static boolean isInternalConstAvailable(File dirFile) {
    if (dirFile.isDirectory()) {
      for (File curFile : dirFile.listFiles()) {
        if ((!curFile.isDirectory()) && curFile.getName().equals(INTERNAL_R_FILENAME)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void loadConstFromFile(DefaultXMLParser parser) {
    String fileName = getCompatibleAPILevelFile(Configs.numericApiLevel);
    loadConstFromFile(parser, fileName);
  }

  private static void loadConstFromFile(DefaultXMLParser parser, String fileName) {
    if (parser == null || fileName == null)
      return;

    try {
      FileReader fr = new FileReader(fileName);
      BufferedReader br = new BufferedReader(fr);
      String curLine;
      boolean isInClass = false;
      String tag = "";
      while ((curLine = br.readLine()) != null){
        curLine = curLine.trim();
        if (curLine.equals("}") && isInClass){
          isInClass = false;
          tag = "";
          continue;
        }
        String[] prefixs = curLine.split(" ");
        if (prefixs.length < 4)
          continue;
        if (prefixs[3].equals("class") && (!isInClass)) {
          //Class declearation
          tag = prefixs[4];
          isInClass = true;
          if (Configs.verbose) {
            Logger.verb("RCONST", "Tag : " + tag + " matched " + curLine);
          }
          continue;
        }

        if (isInClass && curLine.equals("{"))
          continue;

        if (isInClass) {
          if (prefixs.length < 6)
            Logger.verb("RCONST", curLine + "Length lt 6");
          if (!prefixs[5].equals("="))
            continue;
          String name = prefixs[4];
          if (prefixs[6].endsWith(";"))
            prefixs[6] = prefixs[6].substring(0, prefixs[6].length() - 1);
          Integer val;
          try {
             val = Integer.parseInt(prefixs[6]);
          } catch(Exception e) {
            continue;
          }
          parser.feedIdIntoGeneralMap(tag, name, val, true);
        }
      }
      br.close();
      fr.close();
    }catch (IOException e){

    }
    finally {

    }
  }
}
