/* 
 * HeapMemUsage.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package rua;

public class HeapMemUsage implements MemUsage {
  public int nativeSize;
  public int dalvikSize;
  public int nativeAllocated;
  public int dalvikAllocated;

  public HeapMemUsage(int nativeSize, int dalvikSize, int nativeAllocated, int dalvikAllocated) {
    this.nativeSize = nativeSize;
    this.dalvikSize = dalvikSize;
    this.nativeAllocated = nativeAllocated;
    this.dalvikAllocated = dalvikAllocated;
  }
}
