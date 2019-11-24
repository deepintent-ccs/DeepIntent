/*
 * PropertyManager.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Iterator;
import java.util.Set;

import presto.android.Hierarchy;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NStringConstantNode;
import presto.android.gui.graph.NStringIdNode;
import presto.android.xml.XMLParser;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

public class PropertyManager {
  private static PropertyManager theInstance;

  Hierarchy hier = Hierarchy.v();
  XMLParser xml = XMLParser.Factory.getXMLParser();

  PropertyManager() {

  }

  public static synchronized PropertyManager v() {
    if (theInstance == null) {
      theInstance = new PropertyManager();
    }
    return theInstance;
  }

  // === public interfaces

  /*
   * Given an object node, returns possible values of its title.
   */
  public Set<String> getTextsOrTitlesOfView(NObjectNode view) {
    Iterator<NNode> textNodes = view.getTextNodes();
    Set<String> titles = Sets.newHashSet();
    while (textNodes.hasNext()) {
      NNode textNode = textNodes.next();
      String title = textNodeToString(textNode);
      if (title != null) {
        titles.add(title);
      }
    }
    return titles;
  }

  final static String SEPARATOR = "8AwrACha";

  public String getSpeciallySeparatedTextOrTitlesOfView(NObjectNode view) {
    Set<String> titleSet = getTextsOrTitlesOfView(view);
    if (titleSet == null || titleSet.isEmpty()) {
      return null;
    }
    return Joiner.on(SEPARATOR).join(titleSet);
  }

  public String getCommaSeparatedTextsOrTitlesOfView(NObjectNode view) {
    Set<String> titleSet = getTextsOrTitlesOfView(view);
    if (titleSet == null || titleSet.isEmpty()) {
      return null;
    }
    return Joiner.on(',').join(titleSet);
  }

  public String textNodeToString(NNode textNode) {
    if (textNode instanceof NStringConstantNode) {
      return ((NStringConstantNode)textNode).value;
    } else if (textNode instanceof NStringIdNode) {
      Integer stringId = ((NStringIdNode)textNode).getIdValue();
      if (stringId == null) {
        return null;
      }
      return xml.getStringValue(stringId);
    } else {
      throw new RuntimeException("Unknown textNode " + textNode);
    }
  }
}
