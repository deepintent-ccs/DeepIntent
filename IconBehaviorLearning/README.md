# Icon Behavior Learning

Icon behavior learning contains the deep learning model to learn UI widget's feature. It uses DenseNet to extract icon features, bidirectional RNNs to extract text features, and then jointly learn and update the icon features and text features at the same time, with the help and guidance of each other.

## Introduction

This folder mainly contains the model structure, training and predicting codes. It also includes some codes to pre-process the raw data, metrics of prediction results, and so on. Next, we briefly introduce each Python file.

+ conf.py: configuration structure of pre-process, model structure, training and predicting process.
+ layers.py: self defined layers, i.e., DenseNet and co-attention parrallel.
+ metrics.py: to compute the precision, recall of the predicted data.
+ model.py: model structure, could create the model based on ModelConf.
+ pre_process.py: to pre-process the raw data, e.g., tokenize, merge permissions, indexing words.
+ predict.py: load the pre-trained model and predict the pre-processed data.
+ prepare.py: prepare to feed the data into the model, e.g., spliting data, translating to numpy format.
+ tools.py: tools to handle icons, and save or load data.
+ train.py: load the data and train the model.

## Requirements

+ Python >= 3.6.0
+ numpy >= 1.16.0
+ Pillow >= 5.4.1 (PIL)
+ Keras >= 2.2.4
+ nltk >= 3.4.0
+ autocorrect >= 0.4.4

## Entry Point

There are mainly 3 executable Python scripts as entry points:

### Pre-process

Pre-process the raw data that obtained from the contextual text extraction. Directly run `python3 pre_process.py` can handle the data stored in `data/total`, which could be download from the BaiduYun.

### Train

Currently, you may directly run `python3 train.py` can train the model based on the pre-processed benign data.

### Predict

Currently, you may directly run `python3 predict.py` can load the pre-trained model and predict the malicious data.
