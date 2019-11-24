/*
 * IntentFilter.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.intent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import presto.android.Logger;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.PatternMatcher;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * this class is created for handling specification in AndroidManifest.xml
 *
 * http://developer.android.com/guide/components/intents-filters.html according
 * to the guidelines, only three parts are considered
 *
 * action data (both URI and data type) category
 *
 * */
public class IntentFilter {
  private Set<String> mActions;
  private Set<String> mCategories;
  private Set<String> mDataTypes;
  private Set<String> mDataSchemes;
  private List<PatternMatcher> mDataPaths;
  private List<AuthorityEntry> mDataAuthorities;

  // indicate the data type is partial matched or not
  // e.g., "DIR/*" is partial matched, while "DIR/FILE" is full matched
  private boolean mHasPartialTypes;

  public IntentFilter() {
    this.mActions = Sets.newHashSet();
    this.mCategories = Sets.newHashSet();
    this.mDataSchemes = Sets.newHashSet();
    this.mDataTypes = Sets.newHashSet();
    this.mDataAuthorities = Lists.newArrayList();
    this.mDataPaths = Lists.newArrayList();
  }

  public void addAction(String action) {
    this.mActions.add(action);
  }

  public void addCategory(String category) {
    this.mCategories.add(category);
  }
  public final boolean match(IntentAnalysisInfo intentInfo) {
    // check intent has data field or not
    // since we only handle "simple" implicit intent: implicit intent
    // defines no data field
    Map<IntentField, Set<String>> fldInfo = intentInfo.getAllData();
    for (IntentField fld : fldInfo.keySet()) {
      if (fld.isDataField() && !fldInfo.get(fld).isEmpty()) {
        return false;
      }
    }
    // intent filter should also defines no data related fields
    if (!mDataTypes.isEmpty() || !mDataSchemes.isEmpty()
        || !mDataPaths.isEmpty() || !mDataAuthorities.isEmpty()) {
      return false;
    }

    if(!matchAction(intentInfo)) {
      return false;
    }
    if(!matchCategories(intentInfo)) {
      return false;
    }
    return true;
  }
  // the code is from
  // android.content.IntentFilter.match(String,String,String,Uri,Set<String>,String)
  /*
  public final boolean match(IntentAnalysisInfo intentInfo) {
    Set<String> actions = intentInfo.getData(IntentField.Action);
    if (!actions.isEmpty() && !matchAction(actions)) {
      return false;
    }
    boolean dataMatch = matchData(intentInfo);
    if (!dataMatch) {
      return false;
    }
    String categoryMismatch = matchCategories(intentInfo);
    if (categoryMismatch != null) {
      return false;
    }
    return true;
  }
  */
  private final boolean matchCategories(IntentAnalysisInfo intentInfo) {
    Set<String> categories = intentInfo.getData(IntentField.Category);
    if (categories == null) {
      intentInfo.addData(IntentField.Category, IntentAnalysisInfo.DefaultCategory);
      categories = intentInfo.getData(IntentField.Category);
    } else if (!categories.contains(IntentAnalysisInfo.DefaultCategory)) {
      categories.add(IntentAnalysisInfo.DefaultCategory);
    }
    for (String category : categories) {
      if (!mCategories.contains(category)) {
        return false;
      }
    }
    return true;
  }

  // simplified version
  @SuppressWarnings("unused")
  private final boolean matchData(IntentAnalysisInfo intentInfo) {
    Set<String> types = intentInfo.getData(IntentField.MimeType);
    Set<String> schemes = intentInfo.getData(IntentField.Scheme);
    // if types and schemes are empty
    if (mDataTypes.isEmpty() && mDataSchemes.isEmpty()) {
      if (types.isEmpty() && !intentInfo.hasData()) {
        return true;
      } else {
        return false;
      }
    }
    if (!mDataSchemes.isEmpty()) {
      // check schemes
      boolean match = true;
      if (schemes.isEmpty()) {
        match = mDataSchemes.contains("");
      } else {
        for (String scheme : schemes) {
          if (mDataSchemes.contains(scheme) || scheme.equals(IntentAnalysisInfo.Any)) {
            match = true;
            break;
          } else {
            match = false;
          }
        }
      }
      if (!match) {
        return false;
      }
      // there should be a lot of checks here
      final List<AuthorityEntry> authorities = mDataAuthorities;
      if (!authorities.isEmpty()) {
        boolean authMatch = matchDataAuthority(intentInfo);
        if (authMatch) {
          final List<PatternMatcher> paths = mDataPaths;
          if (paths.isEmpty()) {
            match = authMatch;
          } else if (hasDataPath(intentInfo.getData(IntentField.Path))) {
            match = true;
          } else {
            return false;
          }
        } else {
          return false;
        }
      }
    } else {
      boolean match = false;
      for (String scheme : schemes) {
        if (scheme != null && !"".equals(scheme) && !"content".equals(scheme)
            && !"file".equals(scheme) && !IntentAnalysisInfo.Any.equals(scheme)) {
          match = false;
        } else {
          match = true;
          break;
        }
      }
      if (!match) {
        return false;
      }
    }
    if (!mDataTypes.isEmpty()) {
      if (findMimeType(types)) {
        return true;
      } else {
        return false;
      }
    } else {
      if (!types.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private final boolean hasDataPath(Set<String> paths) {
    if (mDataPaths == null || mDataPaths.isEmpty()) {
      return false;
    }
    final int numDataPaths = mDataPaths.size();
    for (int i = 0; i < numDataPaths; i++) {
      final PatternMatcher pe = mDataPaths.get(i);
      for (String path : paths) {
        if (path.equals(IntentAnalysisInfo.Any) || pe.match(path)) {
          return true;
        }
      }
    }
    return false;
  }

  private final boolean matchDataAuthority(IntentAnalysisInfo intentInfo) {
    if (mDataAuthorities == null) {
      return false;
    }
    final int numDataAuthorities = mDataAuthorities.size();
    for (int i = 0; i < numDataAuthorities; i++) {
      final AuthorityEntry ae = mDataAuthorities.get(i);
      boolean match = ae.match(intentInfo);
      if (match) {
        return match;
      }
    }
    return false;
  }

  private final boolean findMimeType(Set<String> types) {
    if (types.isEmpty()) {
      return false;
    }
    for (String type : types) {
      if (mDataTypes.contains(type) || type.equals(IntentAnalysisInfo.Any)) {
        return true;
      }
      // Deal with an Intent wanting to match every type in the IntentFilter.
      final int typeLength = type.length();
      if (typeLength == 3 && type.equals("*/*")) {
        if (!mDataTypes.isEmpty()) {
          return true;
        }
      }
      // Deal with this IntentFilter wanting to match every Intent type.
      if (mHasPartialTypes && mDataTypes.contains("*")) {
        return true;
      }
      final int slashpos = type.indexOf('/');
      if (slashpos > 0) {
        if (mHasPartialTypes
            && mDataTypes.contains(type.substring(0, slashpos))) {
          return true;
        }
        if (typeLength == slashpos + 2 && type.charAt(slashpos + 1) == '*') {
          // Need to look through all types for one that matches
          // our base...
          for (String v : mDataTypes) {
            if (type.regionMatches(0, v, 0, slashpos + 1)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private final boolean matchAction(IntentAnalysisInfo intentInfo) {
    Set<String> intentActions = intentInfo.getData(IntentField.Action);
    if (intentActions != null && !intentActions.isEmpty()) {
      for (String intentAction : intentActions) {
        if (mActions.contains(intentAction)) {
          return true;
        }
      }
      return false;
    } else {
      // if no action is defined in intent, pass
      return true;
    }
  }

  public void addDataPath(String path, int type) {
    addDataPath(new PatternMatcher(path.intern(), type));
  }

  private final void addDataPath(PatternMatcher path) {
    mDataPaths.add(path);
  }

  public final void addDataType(String type) {
    final int slashpos = type.indexOf('/');
    final int typelen = type.length();
    if (slashpos > 0 && typelen >= slashpos + 2) {
      if (typelen == slashpos + 2 && type.charAt(slashpos + 1) == '*') {
        String str = type.substring(0, slashpos);
        if (!mDataTypes.contains(str)) {
          mDataTypes.add(str);
        }
        mHasPartialTypes = true;
      } else {
        if (!mDataTypes.contains(type)) {
          mDataTypes.add(type);
        }
      }
      return;
    }

    Logger.err(getClass().getSimpleName(), "the data mimetype is malformed, please check it :" + type);
  }

  public final void addDataAuthority(String host, String port) {
    if (port != null)
      port = port.intern();
    if (host != null)
      addDataAuthority(new AuthorityEntry(host.intern(), port));
  }

  public final void addDataAuthority(AuthorityEntry ent) {
    mDataAuthorities.add(ent);
  }

  public final void addDataScheme(String scheme) {
    if (!mDataSchemes.contains(scheme)) {
      mDataSchemes.add(scheme);
    }
  }

  public Set<String> getActions() {
    return mActions;
  }

  public Set<String> getCategories() {
    return mCategories;
  }
  @Override
  public String toString() {
    String str = "actions: " + mActions;
    str += "; categories: " + mCategories;
    str += "; schemes: " + mDataSchemes;
    str += "; types: " + mDataTypes;
    str += "; authorities: " + mDataAuthorities;
    return str;
  }

  public boolean isLauncherFilter() {
    return mActions.contains(WTGUtil.v().launcherAction) && mCategories.contains(WTGUtil.v().launcherCategory);
  }

  public final static class AuthorityEntry {
    @SuppressWarnings("unused")
    private final String mOrigHost;
    private final String mHost;
    private final boolean mWild;
    private final int mPort;

    public AuthorityEntry(String host, String port) {
      mOrigHost = host;
      mWild = host.length() > 0 && host.charAt(0) == '*';
      mHost = mWild ? host.substring(1).intern() : host;
      mPort = port != null ? Integer.parseInt(port) : -1;
    }

    public boolean match(IntentAnalysisInfo intentInfo) {
      Set<String> hosts = intentInfo.getData(IntentField.Host);
      Set<String> matchedHosts = Sets.newHashSet();
      if (hosts.isEmpty()) {
        return false;
      }
      if (mWild) {
        for (String host : hosts) {
          if (host.equals(IntentAnalysisInfo.Any)) {
            matchedHosts.add(host);
            continue;
          }
          if (host.length() < mHost.length()) {
            continue;
          }
          host = host.substring(host.length() - mHost.length());
          matchedHosts.add(host);
        }
      }
      for (String host : matchedHosts) {
        if (host.compareToIgnoreCase(mHost) != 0 && !host.equals(IntentAnalysisInfo.Any)) {
          continue;
        }
        if (mPort >= 0) {
          boolean match = true;
          Set<String> ports = intentInfo.getData(IntentField.Port);
          for (String port : ports) {
            if (port.equals(IntentAnalysisInfo.Any) || mPort == Integer.parseInt(port)) {
              match = true;
              break;
            } else {
              match = false;
            }
          }
          if (!match) {
            break;
          } else {
            return true;
          }
        }
        return true;
      }
      return false;
    }

    public String toString() {
      return "<host: " + mHost + ", port: " + mPort + ">";
    }
  }
}
