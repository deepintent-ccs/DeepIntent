/*
 * MultiMapUtil.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

// Handy helper methods for multimap (key -> set(value)).
public class MultiMapUtil {
  public static <Key, HashSetElement, SubClassOfHashSetElement extends HashSetElement>
  void addKeyAndHashSetElement(
      Map<Key, Set<HashSetElement>> map, Key key, SubClassOfHashSetElement element) {
    Set<HashSetElement> set = map.get(key);
    if (set == null) {
      set = Sets.newHashSet();
      map.put(key, set);
    }
    set.add(element);
  }

  public static <Key, HashSetElement, SubClassOfHashSetElement extends HashSetElement>
  void addKeyAndHashSet(
      Map<Key, Set<HashSetElement>> map, Key key, Set<SubClassOfHashSetElement> elements) {
    Set<HashSetElement> set = map.get(key);
    if (set == null) {
      set = Sets.newHashSet();
      map.put(key, set);
    }
    set.addAll(elements);
  }

  public static <Key, HashSetElement> Set<HashSetElement>
  getNonNullHashSetByKey(Map<Key, Set<HashSetElement>> map, Key key) {
    Set<HashSetElement> set = map.get(key);
    if (set == null) {
      set = Collections.emptySet();
    }
    return set;
  }

  public static <Key, HashSetElement, SubClassOfHashSetElement extends HashSetElement>
  boolean contains(Map<Key, Set<HashSetElement>> map, Key key, SubClassOfHashSetElement element) {
    Set<HashSetElement> set = map.get(key);
    return (set != null && set.contains(element));
  }
}
