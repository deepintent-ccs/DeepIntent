import os, sys
import json, subprocess, glob
import tempfile, shutil
import threading

GLOBAL_DECODE_LOCK = threading.Lock()

class GlobalConfigs:
    def __init__(self):
      self.APK_NAME=""
      self.GATOR_ROOT=""
      self.ADK_ROOT=""
      self.APKTOOL_PATH=""
      self.GATOR_OPTIONS=[]
      self.KEEP_DECODE=True

def fatalError(str):
    print(str)
    sys.exit(1)
    pass

def extractLibsFromPath(pathName):
    if not pathExists(pathName):
        return ""
        pass
    fileList = glob.glob(pathName+"/*.jar")
    if len(fileList) == 0:
        return ""
    ret = ""
    for item in fileList:
        ret += ':' + item
    return ret;

def pathExists(pathName):
    if os.access(pathName, os.F_OK):
        return True
    return False
    pass

def invokeGatorOnAPK(\
                apkPath,
                resPath,
                manifestPath,
                apiLevel,
                sdkLocation,
                benchmarkName,
                options,
                configs,
                output = None,
                timeout = 0
                ):
    ''''''
    SootAndroidLocation = configs.GATOR_ROOT + "/SootAndroid"
    bGoogleAPI = False
    if len(apiLevel) > 6:
        if apiLevel[:6] == "google":
            bGoogleAPI = True
    sLevelNum = apiLevel[apiLevel.find('-') + 1:]
    try:
        iLevelNum = int(sLevelNum)
    except:
        fatalError("FATALERROR: API Level not valid")
    if (bGoogleAPI):
        GoogleAPIDir = sdkLocation + \
            "/add-ons/addon-google_apis-google-" + sLevelNum
        if not pathExists(GoogleAPIDir) :
            print("Google API Level:" + sLevelNum + "Not installed!")
            sys.exit(-1);
        GoogleAPI = "{0}/libs/maps.jar:{0}/libs/usb.jar:{0}/libs/effects.jar".format(GoogleAPIDir)
    PlatformAPIDir = sdkLocation + \
            "/platforms/" + "android-" + str(iLevelNum)
    ClassPathJar = extractLibsFromPath("{0}/lib".format(SootAndroidLocation));
    ClassPathJar = ":{0}/bin".format(SootAndroidLocation) + ClassPathJar
    PlatformJar = "{0}/platforms/android-".format(sdkLocation) + sLevelNum +"/android.jar"
    PlatformJar+=":" + "{0}/deps/android-support-annotations.jar:{0}/deps/android-support-v4.jar:{0}/deps/android-support-v7-appcompat.jar:{0}/deps/android-support-v7-cardview.jar:{0}/deps/android-support-v7-gridlayout.jar:{0}/deps/android-support-v7-mediarouter.jar:{0}/deps/android-support-v7-palette.jar:{0}/deps/android-support-v7-preference.jar::{0}/deps/android-support-v7-recyclerview.jar".format(SootAndroidLocation)
    if iLevelNum >= 23:
        #include the apache library
        apacheLib = "{0}/platforms/android-".format(sdkLocation) + sLevelNum +"/optional/org.apache.http.legacy.jar"
        if pathExists(apacheLib):
            PlatformJar += ":" + apacheLib
    #Finished computing platform libraries
    callList = [\
                'java', \
                '-Xmx12G', \
                '-classpath', ClassPathJar, \
                'presto.android.Main', \
                '-project', apkPath,\
                '-android', PlatformJar,\
                '-sdkDir', sdkLocation,\
                '-classFiles', apkPath, \
                '-resourcePath', resPath, \
                '-manifestFile', manifestPath,\
                '-apiLevel', "android-" + sLevelNum,\
                '-benchmarkName', benchmarkName,\
                '-guiAnalysis',
                '-listenerSpecFile', SootAndroidLocation + "/listeners.xml",
                '-wtgSpecFile', SootAndroidLocation + '/wtg.xml']
    callList.extend(options);
    print(" ".join(callList))
    if timeout == 0:
        return subprocess.call(callList, stdout = output, stderr = output)
    else:
       try:
         retval = subprocess.call(callList, stdout = output, stderr = output, timeout = timeout)
         return retval
       except subprocess.TimeoutExpired:
         return -50
    pass

def decodeAPK(apkPath, decodeLocation, output = None):
    global GLOBAL_DECODE_LOCK
    GLOBAL_DECODE_LOCK.acquire()
    callList = ['java',\
                '-jar',\
                "apktool.jar",\
                'd', apkPath,\
                '-o', decodeLocation, \
                '-f']
    ret = subprocess.call(callList, stdout = output, stderr = None)
    GLOBAL_DECODE_LOCK.release()
    if ret != 0:
        fatalError("APK Decode Failed!")
    pass

def parseMainParam():
    params = sys.argv
    configs=GlobalConfigs()
    determinGatorRootAndSDKPath(configs)
    i = 0
    while i < len(params) - 1:
        i += 1
        var = params[i]
        if (var[-4:] == ".apk") and (configs.APK_NAME == ""):
            configs.APK_NAME = var
            continue
        if var == "--keep-decoded-apk-dir":
            configs.KEEP_DECODE = True
            continue
        configs.GATOR_OPTIONS.append(var)
        pass
    return configs

def determinAPILevel(dirName, configs):
    return 18
#     targetLevel = 0;
#     if pathExists(dirName + "/apktool.yml"):
#         infoFile = open(dirName + "/apktool.yml", 'r')
#         lines = infoFile.readlines()
#         infoFile.close()
#         for i in range(len(lines)):
#             if "targetSdkVersion" in lines[i]:
#                 targetLevel = extractAPILevelFromLine(lines[i])
#             elif "minSdkVersion" in lines[i]:
#                 minLevel = extractAPILevelFromLine(lines[i])
#                 if minLevel > targetLevel:
#                     targetLevel = minLevel
#                     pass
#                 pass
#             pass
#         if (targetLevel != 0):
#             if pathExists(configs.ADK_ROOT + "platforms/android-" + str(targetLevel)):
#               return targetLevel
#             else:
#               return 18
#         else:
#             return 18
#     else:
#         return 18

def extractAPILevelFromLine(curLine):
    i = curLine.find("'")
    curLine = curLine[i+1:]
    i = curLine.find("'")
    curLine = curLine[:i]
    numLevel = int(curLine)
    return numLevel

def determinGatorRootAndSDKPath(configs):
    gatorRoot = os.environ.get("GatorRoot")
    if gatorRoot != None:
        configs.GATOR_ROOT = gatorRoot
    else:
        curPath = os.getcwd()
        curPathNames = curPath.split('/')
        lastDirName = "";
        for i in reversed(range(len(curPathNames))):
            if curPathNames[i] != '':
                lastDirName = curPathNames[i]
                break
            pass
        if lastDirName == "AndroidBench":
            configs.GATOR_ROOT = getParentDir(curPath)
        else:
            fatalError("GatorRoot environment variable is not defined")
    adkRoot = os.environ.get("ADK")
    if adkRoot != None:
        configs.ADK_ROOT = adkRoot
    else:
        homeDir = os.environ.get("HOME")
        if homeDir == None:
            fatalError("ADK environment variable is not defined")
        if sys.platform == "linux2":
            if pathExists(homeDir + "/Android/Sdk"):
                configs.ADK_ROOT = homeDir+"/Android/Sdk"
            else:
                fatalError("ADK environment variable is not defined")
        elif sys.platform == "darwin":
            if pathExists(homeDir + "/Library/Android/sdk"):
                configs.ADK_ROOT = homeDir + "/Library/Android/sdk"
            else:
                fatalError("ADK environment variable is not defined")
        pass
    pass

def getParentDir(pathName):
    pathName = pathName.strip()
    if pathName[-1] == '/':
        pathName = pathName[:len(pathName) - 1]
    index = 0
    for i in reversed(range(len(pathName))):
        if pathName[i] == '/':
            index = i
            break
            pass
    if index != 0:
        return pathName[:index]
    else:
        fatalError("GatorRoot environment variable not defined")
    pass

def runGatorOnAPKDirect(apkFileName, GatorOptions, keepdecodedDir, output = None, configs = None, timeout = 0):
    if configs == None:
      configs = GlobalConfigs()
    if configs.GATOR_ROOT == "" or configs.ADK_ROOT == "":
        determinGatorRootAndSDKPath(configs)
    decodeDir = tempfile.mkdtemp()
    if output == None:
      print("Extract APK at: " + decodeDir)
    else:
      output.write("Extract APK at: " + decodeDir + "\n")
    configs.KEEP_DECODE = keepdecodedDir
    configs.APK_NAME = apkFileName
    configs.GATOR_OPTIONS = GatorOptions

    decodeAPK(configs.APK_NAME, decodeDir, output = output)
    numAPILevel = determinAPILevel(decodeDir, configs)
    pathElements = apkFileName.split("/")
    apkFileName = pathElements[-1]

    manifestPath = decodeDir + "/AndroidManifest.xml"
    resPath = decodeDir + "/res"
    retval = invokeGatorOnAPK(\
                apkPath = configs.APK_NAME,\
                resPath = resPath, \
                manifestPath = manifestPath,\
                apiLevel = "android-{0}".format(numAPILevel), \
                sdkLocation = configs.ADK_ROOT, \
                benchmarkName = apkFileName,\
                options = configs.GATOR_OPTIONS,
                configs = configs,
                output = output,
                timeout = timeout)

    if not configs.KEEP_DECODE:
        shutil.rmtree(decodeDir)
        if output == None:
          print("Extracted APK resources removed!")
        else:
          output.write("Extracted APK resources removed!")
    return retval

def main():
    configs = parseMainParam();
    return runGatorOnAPKDirect(configs.APK_NAME,\
     configs.GATOR_OPTIONS,\
     configs.KEEP_DECODE, configs = configs)

if __name__ == '__main__':
    main()
