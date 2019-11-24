package edu.cwru.android.ui;


import brut.androlib.AndrolibException;
import brut.androlib.res.data.*;
import brut.androlib.res.decoder.*;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;
import brut.util.OSDetection;

import java.io.*;

import org.apache.logging.log4j.LogManager;

/**
 * Adapted from ApkTool's AndrolibResources class
 * 
 * 
 * @author Xusheng Xiao
 * @version 0.1
 */
public class ResLoader {

	private static org.apache.logging.log4j.Logger log = LogManager.getFormatterLogger(APKRenderer.class);
	private int FrameworkResID = 0;
	private ResPackage FrameworkRes = null;
	private File mFrameworkDirectory = null;

	public ResLoader() {
//		try {
//			loadFrameworkPkg();
//		} catch (AndrolibException e) {
//			log.error(e);
//		}
	}

	public boolean isFrameworkLoaded() {
		return FrameworkRes != null;
	}

	// User package usually starts with this ID
	private static final int APPARSC_FAKE_ID = 0x7f;
	// A stub package name to be corrected later (when loading XML)
	private static final String APPARSC_FAKE_NAME = "no.arsc";

	public ResTable getResTable(File apkfile) throws AndrolibException {
		ResTable resTable = new ResTable();
		if (FrameworkRes != null) {
			resTable.addPackage(FrameworkRes, false);
		}

		if (apkfile != null) {
			loadMainPkg(resTable, new ExtFile(apkfile));
		} else {
			resTable.addPackage(new ResPackage(resTable, APPARSC_FAKE_ID, APPARSC_FAKE_NAME), true);
		}
		return resTable;
	}

	private static boolean sKeepBroken = false;
	private static String PACKAGEGRP_ANDROID = "android";

	public ResPackage selectPkgWithMostResSpecs(ResPackage[] pkgs) throws AndrolibException {
		int id = 0;
		int value = 0;

		for (ResPackage resPackage : pkgs) {
			if (resPackage.getResSpecCount() > value && !resPackage.getName().equalsIgnoreCase("android")) {
				value = resPackage.getResSpecCount();
				id = resPackage.getId();
			}
		}

		// if id is still 0, we only have one pkgId which is "android" -> 1
		return (id == 0) ? pkgs[0] : pkgs[1];
	}

	public ResPackage loadMainPkg(ResTable resTable, ExtFile apkFile) throws AndrolibException {
		ResPackage[] pkgs = getResPackagesFromApk(apkFile, resTable, sKeepBroken);
		ResPackage pkg = null;

		switch (pkgs.length) {
		case 1:
			pkg = pkgs[0];
			break;
		case 2:
			if (pkgs[0].getName().equals("android")) {
				log.warn("Skipping \"android\" package group");
				pkg = pkgs[1];
				break;
			} else if (pkgs[0].getName().equals("com.htc")) {
				log.warn("Skipping \"htc\" package group");
				pkg = pkgs[1];
				break;
			}

		default:
			pkg = selectPkgWithMostResSpecs(pkgs);
			break;
		}

		if (pkg == null) {
			throw new AndrolibException("arsc files with zero packages or no arsc file found.");
		}

		resTable.addPackage(pkg, true);
		return pkg;
	}

	public File getFrameworkDir() throws AndrolibException {
		if (mFrameworkDirectory != null) {
			return mFrameworkDirectory;
		}

		String path;

		// if a framework path was specified on the command line, use it

		File parentPath = new File(System.getProperty("user.home"));
		if (!parentPath.canWrite()) {
			log.error(String.format("WARNING: Could not write to $HOME (%s), using %s instead...",
					parentPath.getAbsolutePath(), System.getProperty("java.io.tmpdir")));
			log.error("Please be aware this is a volatile directory and frameworks could go missing, "
					+ "please utilize --frame-path if the default storage directory is unavailable");

			parentPath = new File(System.getProperty("java.io.tmpdir"));
		}

		if (OSDetection.isMacOSX()) {
			path = parentPath.getAbsolutePath()
					+ String.format("%1$sLibrary%1$sapktool%1$sframework", File.separatorChar);
		} else if (OSDetection.isWindows()) {
			path = parentPath.getAbsolutePath()
					+ String.format("%1$sAppData%1$sLocal%1$sapktool%1$sframework", File.separatorChar);
		} else {
			path = parentPath.getAbsolutePath()
					+ String.format("%1$s.local%1$sshare%1$sapktool%1$sframework", File.separatorChar);
		}

		File dir = new File(path);

		if (dir.getParentFile() != null && dir.getParentFile().isFile()) {
			log.error("Please remove file at " + dir.getParentFile());
			System.exit(1);
		}

		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new AndrolibException("Can't create directory: " + dir);
			}
		}

		mFrameworkDirectory = dir;
		return dir;
	}

	public void loadFrameworkPkg() throws AndrolibException {
		ResPackage[] pkgs = null;
		String frameworkapk = getFrameworkDir() + "/1.apk";
		log.info("Loading resource table from file: " + frameworkapk);
		pkgs = getResPackagesFromApk(new ExtFile(frameworkapk), true);

		if (pkgs != null && pkgs.length > 0)
			FrameworkRes = pkgs[0];

		if (FrameworkRes != null) {
			FrameworkResID = FrameworkRes.getId();
		}
	}

	private ResPackage[] getResPackagesFromApk(ExtFile apkFile, ResTable resTable, boolean keepBroken)
			throws AndrolibException {
		try {
			BufferedInputStream bfi = new BufferedInputStream(apkFile.getDirectory().getFileInput("resources.arsc"));
			return ARSCDecoder.decode(bfi, false, keepBroken, resTable).getPackages();
		} catch (DirectoryException ex) {
			throw new AndrolibException("Could not load resources.arsc from file: " + apkFile, ex);
		}
	}

	private ResPackage[] getResPackagesFromApk(ExtFile apkFile, boolean keepBroken) throws AndrolibException {
		try {
			BufferedInputStream bfi = new BufferedInputStream(apkFile.getDirectory().getFileInput("resources.arsc"));
			return ARSCDecoder.decode(bfi, false, keepBroken).getPackages();
		} catch (DirectoryException ex) {
			throw new AndrolibException("Could not load resources.arsc from file: " + apkFile, ex);
		}
	}

}
