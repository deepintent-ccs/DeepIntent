# Contextual Text Extraction

After the static analysis process, we next extract contextual texts. This step also locate the target icon data based on the static analysis results.

## Introduction

This folder mainly contains the codes to extract layout texts, embedded texts and resource texts. The inputs of this step are the program analysis results and decoded APK files, and outputs <icon, texts, permission> triple to train the model.

Additionally, to extract the embedded texts, we also have to select the largest icon from the same icon with different settings (e.g., different resolution). It might also translate any other languages to English to increase the text quality.

The content Python files are described as follows.

+ conf.py: the configuration of the extraction steps.
+ extract_text.py: the main entry of this step, use `ExtractionConf` (defined in conf.py) to direct the path and other options.
+ handle_embedded_text.py: select target icon and extract embedded texts by using OCR technique.
+ handle_layout_text.py: extract layout texts, could extract texts from parent layout or the whole layout page.
+ handle_resource_text.py: get resource texts by spliting `_` and camel case.
+ load_data.py: load program analysis results, could be raw `.csv` file or zipped `.zip` file.
+ tools.py: tools to parse XML, handle icons, and save or load data.
+ translate_text.py: translate any other language to English, optional.

## Requirements

+ Python >= 3.6.0
+ numpy >= 1.16.0
+ opencv-python >= 3.4.3 (cv2)
+ pytesseract >= 0.2.6
+ Pillow >= 5.4.1 (PIL)
+ translate >= 3.4.0 (optional, to translate any other language to English through Internet)

## Entry Point

There are mainly 5 executable Python scripts as entry points:

### 1. extract_text.py

The overall entry of contextual text extraction.
Given static analysis results and decoded APKs, this program will locate the largest icon and extract layout texts, embedded texts and resource texts. *Different from the paper, we additionally add a translate unit to translate texts that are written in any other languages into English. We believe this step will enhance DeepIntent's generalization ability.*

Basically, the inputs and outputs of this program are:

+ Input.
Paths of 
1) program analysis results, could be '.csv' or zipped '.csv', 
2) decoded apps, the program assume all the related apps are decoded in one folder,
3) pre-trained EAST model (could be download from BaiduYun), which is used in embedded text extraction.

+ Output.
`<compressed image, texts, permissions>` triples, which is saved in Python's PKL format.

We also add 2 simple argument to make it easy to run the example program:

+ `--example`. The program will execute with data located in `data/text_example/total` (provided in Github). Please first decode the apps into `apk_decoded` folder (or run `decode.py`).

+ `--total-example`. The program will execute with data located in `data/example` (provided in BaiduYun). Please first decode both benign apps and malicious apps.

### 2. handle_layout_text.py

Extract layout texts based on icon name and its layout file.

+ Input.
1) image name (provided by static analysis results),
2) path of layout file, 3) path of `String.xml` file.
+ Output. 
Layout texts.

You may also use `--example` parameter to run example program, which extracts layout texts from `data/text_example/layout`.

### 3. handle_embedded_text.py

Extract embedded texts based on icon graphics.

+ Input.
Path of the target icon.
+ Output.
Embedded texts.

Argument `--example` will extract embedded texts from `data/text_example/ocr`.

### 4. handle_resource_text.py

Split icon name as resource texts. Considered Under-Score-Case and Camel-Case variables.

+ Input.
Icon variable name.
+ Output.
Resource texts.

### 5. translate_text.py

Translate any other language into English.

+ Input. 
Texts in UTF-8 format.
+ Output.
Translated English texts, or error when results are all in upper case.
