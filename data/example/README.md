# Example APKs

This is the example designed to show how to config and run DeepIntent. This example will start from raw input APK files, please download the example APKs from [BaiduYun](https://pan.baidu.com/s/1mcDk1tDnkI-PdW3kpwZ3lQ), whose password is 19ah.

The downloaded data contains 2 folders, `benign` folder contains benign APKs (download from Google Store) and `malicious` folder contains malicious APKs (detected by several anti-virus engine and removed from app stores). These APKs are randomly selected from our experiments. It contains 72 benign apps and 30 malicious apps. In our experiments, we use benign apps to train the model, and use malicious apps to test.

DeepIntent mainly contains 3 phases, namely *1) icon widget analysis*, *2) contextual text extraction*, and *3) intention behavior discrepancy*. Source codes of each phase is stored seperately in the root folder. Notice that, the latter phase depends on the previous phase's output, please excute them with the correct order.

Next, we will introduce each phase one by one.

## Icon Widget Analysis

Icon widget analysis aims to extract `<icon, text, permission>` triple to train the model. `Static analysis` is the program analysis step, which extracts UI widget's name and its related sensitive permissions. `Contextual text extraction` is to extract the icon data, contextual texts based on the UI widget's name.

### Static Analysis

`Static analysis` leverages several existing works (i.e., GATOR, IconIntent) to establish mappings between UI widgets in Android apps and their corresponding

+ Input
+ Output

### Contextual Text Extraction

+ Input
+ Output

Please check []() for more details.

## IconBehaviorLearning

## IntentionBehaviorDiscrepancy
