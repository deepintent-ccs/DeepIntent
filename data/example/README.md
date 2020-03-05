# Example APKs

This is the example designed to show how to config and run DeepIntent. This example will start from raw input APK files, please download the example APKs from [BaiduYun](https://pan.baidu.com/s/10HSPVJxEOkFOLSQQOiyjbg), whose password is *mxjc*.

The downloaded data contains 2 folders, `benign` folder contains benign APKs (download from Google Store) and `malicious` folder contains malicious APKs (detected by several anti-virus engine and removed from app stores). These APKs are randomly selected from our experiments. It contains 72 benign apps and 30 malicious apps. In our experiments, we use benign apps to train the model, and use malicious apps to test.

DeepIntent mainly contains 3 phases, namely *1) icon widget analysis*, *2) icon behavior learning*, and *3) intention behavior discrepancy*. Source codes of each phase is stored seperately in the root folder. Notice that, the latter phase depends on the previous phase's output, please excute them with the correct order.

These examples are mainly designed to show the first phase, as the data size of the example APKs is relatively small, directly use these data to train the deep learning or outlier detection model might not perform well. To see the following two phases, please refer to [total data](../total) or readme of [learning ](../../IconBehaviorLearning) and [outlier](../../IntentionBehaviorDiscrepancy) phase for more details.

Next, we will introduce how to use the data to run icon widget analysis phase.

## Icon Widget Analysis

Icon widget analysis aims to extract `<icon, text, permission>` triple to train the model. `Static analysis` is the program analysis step, which extracts UI widget's name and its related sensitive permissions. `Contextual text extraction` is to extract the icon data, contextual texts based on the UI widget's name.

### Static Analysis

Static analysis leverages several existing works (i.e., GATOR, IconIntent) to establish mappings between UI widgets in Android apps and their corresponding permissions.

+ Input
Android APKs, may also need GATOR, APKTool, Android SDKs and other requirements as runtime enviroments.

+ Output
CSV file, that each row contains, APK name, UI widget name, layout, handler, related permissions and other useful information.

To run the example, please refer to [Static Analysis](../../IconWidgetAnalysis/Static_Analysis) part and following the guides to replace the blank directory listed in `runImg2widgets.sh` and config the enviroments, then run the script.

This folder also contains zipped program analysis' results of example APKs, i.e., `benign_pa.zip` and `malicious_pa.zip`. They are zipped from the resulting CSV files to save space. These files may also be the input of the following example phases.

### Contextual Text Extraction

Contextual text extraction is to extract icon, contextual texts of the UI widgets based on program analysis results. You can simply run `python3 extract_text.py --total_example` (contained in folder `ContextualTextExtraction`) to get the results.

+ Input
1) program analysis results, e.g., `benign_pa.zip`, 2) decoded APKs, please first decode these APKs into decoded folder, e.g. decode APKs that are in `benign` folder into `benign_decoded` folder, 3) pre-trained EAST model.

+ Output
Raw data that contains compressed icon, texts, and permissions. The data is stored in Python's PKL folder.

Please check [ContextualTextExtraction](../../IconWidgetAnalysis/ContextualTextExtraction) for more details.
