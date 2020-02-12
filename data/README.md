# Data set

## Unit test examples

In github, we only upload small example data to run each step's unit tests. Here are the contents:

### text_example.

Small examples used to test textual extraction process.

The related source codes are stored in `IconWidgetAnalysis/ContextualTextExtraction`. They are written in Python and could be execute with parameter `--example` to execute these examples. Notice that, these Python scripts use relative path to find the data, so please run the Python scripts in its own folder.

+ layout/: 
The `layout` folder contains example data to extract layout texts, each layout related to 2 xml files, `.xml` file means the layout file, `.strings.xml` file means the string dictionary file which maps `@string/id` to real texts. 
To run the example, please change to `IconWidgetAnalysis/ContextualTextExtraction` folder and run `Python3 handle_layout_text --example`, other meta data is also defined in the Python file.
+ ocr/:
The `ocr` folder contains example icons that have embedded texts, we presented Chinese, English, Japanese, and Korean examples. The name of each icon is seperated with `_`, the first part is the target language, and the second part could be the identifier of the icon (e.g., index is used in presented examples).
To run the example, please change to `IconWidgetAnalysis/ContextualTextExtraction` folder and run `Python3 handle_embedded_text --example`. The script will extract each icon stored in `ocr/` folder one by one. Please 1) install pytesseract correctly, 2) download the pre-trained EAST model through BaiduYun([link](https://pan.baidu.com/s/1mcDk1tDnkI-PdW3kpwZ3lQ), password: 19ah) and put it in `data/frozen_east_text_detection.pb`.
+ total/:
The `total` folder contains example APKs (stored in `apk` folder) and static analysis results (stored in `example.zip`).
To run the example, please decode the APKs into `decoded_apk` folder and then run `Python3 extract_text --example` from `IconWidgetAnalysis/ContextualTextExtraction` folder.

For more details (e.g., requirements and arguments) about contextual text extraction scripts, please refer to [ContextualTextExtraction](../IconWidgetAnalysis/ContextualTextExtraction) part.

## Full process examples

We also provided training and testing data through BaiduYun([link](https://pan.baidu.com/s/1mcDk1tDnkI-PdW3kpwZ3lQ), password: 19ah). Here are the structure of the download data:

### example.
the small example APKs, including both benign and malicious apps, should be download from the above URL.

### total.
the pre-processed data used in the experiments, should be download from the above URL.

### frozen_east_text_detection.pb.
A pre-trained EAST model, which is required to extract embedded texts.
