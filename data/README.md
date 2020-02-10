Data set
====================

Unit test examples
------------------------

In github, we only upload small example data to run each step's unit tests. Here are the contents:

### text_example.

Small examples used to test textual extraction process.

The related source codes are stored in `IconWidgetAnalysis/ContextualTextExtraction`. They are written in Python and could be execute with parameter `--example` to execute these examples. Notice that, these Python scripts use relative path to find the data, so please run the Python scripts in its own folder.

The requirements and arguments could refer to [ContextualTextExtraction](../IconWidgetAnalysis/ContextualTextExtraction).

Full process examples
------------------------

We also provided training and testing data through BaiduYun([link](https://pan.baidu.com/s/1mcDk1tDnkI-PdW3kpwZ3lQ), password: 19ah). Here are the structure of the download data:

### example.
the small example APKs, including both benign and malicious apps, should be download from the above URL.

### total.
the pre-processed data used in the experiments, should be download from the above URL.

### frozen_east_text_detection.pb.
a pre-trained EAST model.
