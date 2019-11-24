import os
import io
import sys
import glob
import shutil
import tempfile
import subprocess
import multiprocessing as mp

g_app_dir = sys.argv[1]
g_adk_dir = sys.argv[2]
g_gator_root = sys.argv[3]
g_process_size = 6
g_timeout = 3600
g_start_index = 0
g_stop_index = 28

g_lock = None
g_skip_handled = True
g_redo_failed = False
g_fp_result = "result.txt"  # file path


class GlobalConfigs:
    def __init__(self):
        self.APK_PATH = ""
        self.GATOR_ROOT = ""
        self.ADK_ROOT = ""
        self.GATOR_OPTIONS = []
        self.KEEP_DECODE = False


def parse_gator_params(params):
    configs = GlobalConfigs()
    determine_gator_root(configs)
    determine_sdk_path(configs)

    for param in params:
        if (param[-4:] == ".apk") and (configs.APK_PATH == ""):
            configs.APK_PATH = param
            continue
        if param == "--keep-decoded-apk-dir":
            configs.KEEP_DECODE = True
            continue
        configs.GATOR_OPTIONS.append(param)
    return configs


def determine_api_level(dir_base, configs):
    return 18


def determine_sdk_path(configs):
    configs.ADK_ROOT = os.environ.get("ADK")


def determine_gator_root(configs):
    configs.GATOR_ROOT = os.environ.get("GatorRoot")


def extract_jar_libs(path_base):
    if not os.path.exists(path_base):
        return ""
    jars = glob.glob(path_base + "/*.jar")
    return ":" + ":".join(jars) if len(jars) > 0 else ""


def decode_apk(apk_path, decode_path, frame_path, output=None):
    cmd = ["java", "-jar", "apktool.jar", "d", apk_path,
           "--frame-path", frame_path,
           "-o", decode_path, "-f"]

    return subprocess.run(cmd, stdout=output, stderr=None)


def invoke_gator_on_apk(
        apk_path,
        res_path,
        manifest_path,
        api_level,
        android_sdk_path,
        benchmark_name,
        options,
        configs,
        output=None,
        timeout=0,
):
    soot_android_path = os.path.join(configs.GATOR_ROOT, "SootAndroid")
    s_level_num = api_level[api_level.find('-') + 1:]
    i_level_num = int(s_level_num)

    platform_api_dir = os.path.join(android_sdk_path, "platforms", "android-" + str(i_level_num))
    cp_jars = extract_jar_libs(os.path.join(soot_android_path, "lib"))
    cp_jars = ":" + os.path.join(soot_android_path, "bin") + cp_jars

    platform_jars = os.path.join(platform_api_dir, "android.jar")
    platform_jars += ":{0}/deps/android-support-annotations.jar" \
                     ":{0}/deps/android-support-v4.jar" \
                     ":{0}/deps/android-support-v7-appcompat.jar" \
                     ":{0}/deps/android-support-v7-cardview.jar" \
                     ":{0}/deps/android-support-v7-gridlayout.jar" \
                     ":{0}/deps/android-support-v7-mediarouter.jar" \
                     ":{0}/deps/android-support-v7-palette.jar" \
                     ":{0}/deps/android-support-v7-preference.jar" \
                     ":{0}/deps/android-support-v7-recyclerview.jar".format(soot_android_path)
    # Finished computing platform libraries

    cmd = ["java", "-Xmx12G",
           "-classpath", cp_jars,
           "presto.android.Main",
           "-project", apk_path,
           "-android", platform_jars,
           "-sdkDir", android_sdk_path,
           "-classFiles", apk_path,
           "-resourcePath", res_path,
           "-manifestFile", manifest_path,
           "-apiLevel", "android-" + s_level_num,
           "-benchmarkName", benchmark_name,
           "-guiAnalysis",
           "-listenerSpecFile", soot_android_path + "/listeners.xml",
           "-wtgSpecFile", soot_android_path + '/wtg.xml']
    cmd.extend(options)
    # print(" ".join(cmd))

    if timeout == 0:
        return subprocess.run(cmd, stdout=output, stderr=output)
    else:
        try:
            return subprocess.run(cmd, stdout=output, stderr=output, timeout=timeout)
        except subprocess.TimeoutExpired:
            return -50


def run_gator(configs, msg, output=None, timeout=0):
    apk_name = os.path.basename(configs.APK_PATH)
    print("<start>{}({})".format(apk_name, msg))
    # create output file
    b_out2file = False
    if output is None:
        output = sys.stdout
    elif isinstance(output, str):
        b_out2file = True
        output = open(output, mode="w")

    # decode apk to temp folder
    decode_dir = tempfile.mkdtemp()
    frame_dir = tempfile.mkdtemp()
    print("Extract APK at:", decode_dir, ". Frameworks:", frame_dir, file=output)
    dec_ret = decode_apk(configs.APK_PATH, decode_dir, frame_dir, output=output)
    dec_ret = dec_ret.returncode
    shutil.rmtree(frame_dir)
    print("Frameworks removed!", file=output)

    if dec_ret != 0:
        if not configs.KEEP_DECODE:
            shutil.rmtree(decode_dir)
            print("Extracted APK resources removed!", file=output)
        if b_out2file:
            output.close()

        return -1, apk_name, msg

    # gator
    api_level = determine_api_level(decode_dir, configs)
    apk_name = os.path.basename(configs.APK_PATH)

    mf_path = decode_dir + "/AndroidManifest.xml"
    res_path = decode_dir + "/res"
    gator_ret = invoke_gator_on_apk(
        apk_path=configs.APK_PATH,
        res_path=res_path,
        manifest_path=mf_path,
        api_level="android-{0}".format(api_level),
        android_sdk_path=configs.ADK_ROOT,
        benchmark_name=apk_name,
        options=configs.GATOR_OPTIONS,
        configs=configs,
        output=output,
        timeout=timeout
    )
    if isinstance(gator_ret, int):
        assert gator_ret == -50
        gator_ret = -2
    elif gator_ret.returncode != 0:
        gator_ret = -3
    else:
        gator_ret = gator_ret.returncode

    if not configs.KEEP_DECODE:
        shutil.rmtree(decode_dir)
        print("Extracted APK resources removed!", file=output)
    if b_out2file:
        output.close()

    return gator_ret, apk_name, msg


def after_gator(args):
    r, apk_name, msg = args
    result_type = {
        0: "SUCCESS",
        -1: "DECODE_ERROR",
        -2: "TIMEOUT",
        -3: "GATOR_ERROR"
    }

    print("<finish>{}({}) {}".format(apk_name, msg, "fail" if r != 0 else "success"))
    g_lock.acquire()
    with open(g_fp_result, mode="a") as fo:
        print("{}\t{}".format(apk_name, result_type[r]), file=fo)
    g_lock.release()
    sys.stdout.flush()


def main():
    # load apps
    apps = os.listdir(g_app_dir)
    print("app total size:", len(apps))

    # export global variable
    os.environ["ADK"] = g_adk_dir
    os.environ["GatorRoot"] = g_gator_root

    # create output dir
    log_dir = "log_output"
    output_dirs = ["output", "dot_output", log_dir]
    for output_dir in output_dirs:
        if not os.path.exists(output_dir):
            os.mkdir(output_dir)

    # skip settings
    apps_handled = []
    if os.path.exists(g_fp_result):
        if not g_skip_handled:
            os.remove(g_fp_result)
        else:
            with open(g_fp_result, mode="r") as fi:
                for line in fi:
                    apk_name, r = line.strip().split("\t")
                    if g_redo_failed and r != "SUCCESS":
                        continue
                    apps_handled.append(apk_name)
            if g_redo_failed:
                with open(g_fp_result, mode="w") as fo:
                    for apk_name in apps_handled:
                        print("{}\t{}".format(apk_name, "SUCCESS"), file=fo)

    # init multi-processing pool
    global g_lock
    pool = mp.Pool(g_process_size)
    g_lock = mp.Lock()

    # init state
    start_index = g_start_index
    stop_index = g_stop_index if g_stop_index > g_start_index else len(apps)
    for i in range(start_index, stop_index):
        app = apps[i]
        if not app.endswith(".apk"):
            continue
        if app in apps_handled:
            print("<handled>{}({}/{})".format(app, i, len(apps)))
            continue

        app_path = os.path.join(g_app_dir, app)
        configs = parse_gator_params([app_path, "-client", "WTGDemoClient"])
        fp_output = os.path.join(log_dir, app.replace(".apk", ".log"))
        msg = "{}/{}".format(i, len(apps))

        # add to thread pool
        # args: configs, msg, output, timeout
        pool.apply_async(func=run_gator,
                         args=(configs, msg, fp_output, g_timeout),
                         callback=after_gator)

    pool.close()
    pool.join()


if __name__ == '__main__':
    main()
