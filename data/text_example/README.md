# Text Example

This folder contains small examples used to test textual extraction process.

## Structure

+ layout/: 
The `layout` folder contains example data to extract layout texts, each layout related to 2 xml files, `.xml` file means the layout file, `.strings.xml` file means the string dictionary file which maps `@string/id` to real texts. 

+ ocr/:
The `ocr` folder contains example icons that have embedded texts, we presented Chinese, English, Japanese, and Korean examples. The name of each icon is seperated with `_`, the first part is the target language, and the second part could be the identifier of the icon (e.g., index is used in presented examples).

+ total/:
The `total` folder contains example APKs (stored in `apk` folder) and static analysis results (stored in `example.zip`).

## How to run

### Layout Text Example

To run the example, please change to `IconWidgetAnalysis/ContextualTextExtraction` folder and run `python3 handle_layout_text --example`, other meta data is also defined in the Python file.

### Embedded Text Example

To run the example, please change to `IconWidgetAnalysis/ContextualTextExtraction` folder and run `Python3 handle_embedded_text --example`. The script will extract each icon stored in `ocr/` folder one by one. Please 1) install pytesseract correctly, 2) download the pre-trained EAST model through BaiduYun and put it in `data/frozen_east_text_detection.pb`.

### Extract All Contextual Texts Example

To run the example, please decode the APKs into `decoded_apk` folder and then run `Python3 extract_text --example` from `IconWidgetAnalysis/ContextualTextExtraction` folder.

For more details (e.g., requirements and arguments), please refer to [Contextual Text Extraction](../IconWidgetAnalysis/ContextualTextExtraction) part.
