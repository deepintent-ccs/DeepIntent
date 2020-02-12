# Contextual Text Extraction

After the static analysis process, we next extract contextual texts. 

## Introduction

This folder mainly contains the model structure, training and predicting codes. It also includes some codes to pre-process the raw data, metrics of prediction results, and so on. Next, we briefly introduce each Python file.

+ conf.py: the configuration of 
+ extract_text.py: 
+ handle_embedded_text.py: 
+ handle_layout_text.py: 
+ handle_resource_text.py: 
+ load_data.py:
+ tools.py:
+ translate_text.py: 

## Requirements

+ Python >= 3.6
+ numpy 
+ 

## Entry Point

There are mainly 3 executable Python scripts as entry points:

In detail, there are mainly related to 3 Python files.

1. `handle_layout_text.py --example` will extract layout texts based on the hard-coded meta data. The meta data is written in the Python file and contains target image name, related layout file, related string dictionary file, extraction type ('parent' or 'total', 'parent' means only extract the texts from the parent layout, and 'total' means to lookup all the layout file). You can run your own data by modify the meta data and provided required files (i.e., layout file and string dictionary file), or use the command line arguments to provide them.

2. `handle_embedded_text.py --example` will extract embedded texts from image by using OCR technique. To run this script, please install pytesseract with at least Chinese, English, Japanese and Korean support first, and download the pre-trained EAST model through the following link. This example will excute each image stored in `data/text_example/ocr`. The image name also describe the corresponding language, e.g., `chinese_1.png` means the image contains Chinese character and the index is 1.

3. `extract_text.py` is a simple text extraction example. Given the image names and decoded APKs, the script will extract layout texts, embedded texts and resource texts for each image, and then output the (APK name, image name, extracted texts) tuple into `data/text_example/total/data.pkl`. The results are stored in Python's PKL format. To run this script, please 1) decode the APKs stored in the `data/text_example/total/apk` into the `data/text_example/total/decoded_apk` folder by using [APKTool](https://ibotpeaches.github.io/Apktool/), 2) download the pre-trained EAST model and put it in `data/frozen_east_text_detection.pb` through the following BaiduYun link.

### Pre-process

### Train

### Predict
