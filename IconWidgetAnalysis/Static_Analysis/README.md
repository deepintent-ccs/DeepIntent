# Static Analysis

## Introduction

The first part of DeepIntent is based on static analysis on Android apps. We leverage several existing works (i.e. GATOR, IconIntent) to establish mappings between UI widgets in Android apps and their corresponding handlers in the source code. Then we apply our static analysis on mapping those handlers to sensitive APIs and permission based on extended call graph we build for each app. The final output of static analysis is associations among UI widgets, their corresponding handlers, sensitive API calls and permissions behind each UI widget.

## Prerequisite

Our approach relies on a local DB to run ic3 and sensitive API calls-permission mapping. You have to configure mysql username and password for ic3 and mapping. The way is to open /Static_Analysis/ic3/cc.properties, change the username and password. And also open /Static_Analysis/APKCallGraph/src/APKCallGraph.java, in line 674 and 675.
The static analysis also takes some configuration files as input. The files are included in this project, the path is in /Static_Analysis/APKCallGraph/src/APKCallGraph.java, at line 270 and line 285.
At last, as a part of the analysis, we also output the extended call graph of each app. The paths are defined at line 501, 503 and 632.

## How to run the code

1. Make a jar file after change every path in /Static_Analysis/APKCallGraph/src/APKCallGraph.java
2. Follow the comments in runImg2widgets.sh, change the dirs correspondingly.
3. Use commend sh runImg2widgets.sh
