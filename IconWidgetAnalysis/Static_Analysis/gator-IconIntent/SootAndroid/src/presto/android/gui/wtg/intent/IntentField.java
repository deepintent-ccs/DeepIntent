/*
 * IntentField.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.intent;

public enum IntentField {
  /////////////////////////////
  // normal section
  NormalStart,
  SrcActivity,
  TgtActivity,
  Action,
  Category,
  MimeType,
  /////////////////////////////
  // uri section
  UriPartStart,
  Scheme,
  Host,
  Port,
  Path,
  /////////////////////////////
  // all sections
  AllStart,
  All,
  ////////////////////////////
  // special key
  UriStart,
  Uri,
  ////////////////////////////
  // implicit target
  ImplicitStart,
  ImplicitTgtActivity,
  END;
  public boolean isNormalField() {
    return this.ordinal() > NormalStart.ordinal() && this.ordinal() < UriPartStart.ordinal();
  }
  public boolean isPartOfUriField() {
    return this.ordinal() > UriPartStart.ordinal() && AllStart.ordinal() > this.ordinal();
  }
  public boolean isAllField() {
    return this.ordinal() > AllStart.ordinal() && UriStart.ordinal() > this.ordinal();
  }
  public boolean isUriField() {
    return this.ordinal() > UriStart.ordinal() && END.ordinal() > this.ordinal();
  }
  public boolean isDataField() {
    // data field includes everything except srcActivity, tgtActivity, action and category
    return this.ordinal() > Category.ordinal() && ImplicitStart.ordinal() > this.ordinal();
  }
}
