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

Pre-process the raw data that obtained from the contextual text extraction.
It filtered strange data (e.g., no image or image is two small), tokenize texts and generate vocabulary, mapping permissions to permission groups, and so on.

+ Input.
Raw data after contextual text extraction (in PKL format).
+ Output.
Data like `[vocab2id dict, label2id dict (permission groups), [pre-processed data]]`, saved in PKL format.

Directly run `python3 pre_process.py` will handle the example results of contextual text extraction (with parameter `--total-example`), please make sure results (i.e., `raw_data.benign.pkl` and `raw_data.mal.pkl`) are stored in `data/example` folder. The output will be saved in the same folder with name `data.benign.pkl` and `data.mal.pkl`.

Note that, the example APKs are much more smaller than the real data set, use them to train might mislead the model, so we provided the total data set to train. The total data set could be download from BaiduYun.

### Train

Split pre-processed data into training, validation and testing sets.
Training and testing the model several times (default is 3 times) and report each time's precision, recall and averaged metrics results.

+ Input.
Pre-processed data, and optional, model structure configuration (hidden layer dims, number of dense blocks, and so on) and training configuration (data split ratio, training patients, and so on).
+ Output.
Evaluation results, and
trained model with training configuration as meta data.

To run the program, please first download pre-processed data from BaiduYun.
Then, directly run `python3 train.py`, which could train the model based on the pre-processed benign data.

### Predict

Load the trained model and evaluate with given/input data.

+ Input.
The trained model, model's meta data and the pre-processed data to be predicted.
+ Output.
Evaluation results, and prediction vectors (also provided real labels to review).

To run the program, please first download pre-processed data from BaiduYun.
Then, directly run `python3 predict.py`, which could load the pre-trained model and predict corresponding split testing data.
