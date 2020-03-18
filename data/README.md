# Data set

## Unit test examples

In github, we only upload small example data to run each step's unit tests. Here are the contents:

### text_example/

Small examples used to test textual extraction process.

The related source codes are stored in `IconWidgetAnalysis/ContextualTextExtraction`.
You may use `python3 [script_name] --example` to execute these examples.

## Full process examples

We also provided training and testing data through BaiduYun([link](https://pan.baidu.com/s/1E0iE-Nm8xx4qsFB6PnkYwA), password: nmqf). Here are the structure of the download data:

### example/
The small example APKs, including both benign and malicious apps, should be download from the above URL.
Please check [example folder](example) for more details.

### total/
The pre-processed data used in the experiments, should be download from the above URL.

### frozen_east_text_detection.pb.
A pre-trained EAST model, which is required to extract embedded texts.
