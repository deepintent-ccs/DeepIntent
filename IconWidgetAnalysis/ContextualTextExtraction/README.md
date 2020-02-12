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

+ Input
+ Output

### 2. handle_layout_text.py

### 3. handle_embedded_text.py

### 4. handle_resource_text.py

### 5. translate_text.py
