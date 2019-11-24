package edu.cwru.android.ui;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;

import brut.androlib.res.data.ResTable;
import brut.androlib.res.decoder.AXmlResourceParser;
import edu.cwru.android.ui.APKResourceResolver.ResLayout;

public abstract class TagXmlHandler {
	public abstract ResLayout handleOneTag(String xmlFile, AXmlResourceParser parser, ResLayout parent);

	ResTable resTable;
	HashMap<Integer, String> id2Name;
	protected PrintWriter out;

	public void setId2Name(HashMap<Integer, String> map) {
		this.id2Name = map;
	}

	public void setResTable(ResTable t) {
		resTable = t;
	}

	public void setPrinter(PrintWriter out) {
		this.out = out;

	}

	public void startOneXml(String xmlname) {

	}

	public void endOneXml(String xmlname) {

	}

	public void endOneApp(String apkfilename) {
		// TODO Auto-generated method stub
		
	}
}
