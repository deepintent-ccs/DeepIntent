# Intention Behavior Discrepancy

Intention behavior discrepancy aims to detect outliers according to co-attentioned feature and prediction result of DeepIntent model. It uses the co-attentioned feature to train the AutoEncoder outlier detection model, then combines distance-based aggregation and prediction-based aggregation to compute final outlier score.

## Introduction

This folder mainly contains the codes to train and evaluate the outlier model. It also includes codes to load the pre-trained deep learning model, load training and labeled testing data, and so on. Next, we briefly introduce each Python file.

+ conf.py: configuration of the pre-trained deep learning model, used to ensure the input data shape.
+ layers.py: co-attention structure, to load the deep learning model.
+ metrics.py: to compute the precision, recall, AUC value of the labeled testing data.
+ outlier.py: training and testing the outlier detection model.

## Requirements

+ Python >= 3.6.0
+ numpy >= 1.16.0
+ Pillow >= 5.4.1 (PIL)
+ Keras >= 2.2.4
+ nltk >= 3.4.0
+ pyod >= 0.7.4
+ sklearn >= 0.21.1
+ matplotlib >= 3.0.2, optional, to plot precision and recall curve

## Entry Point

There are mainly 1 executable Python scripts as entry point:

### Outlier

Load the pre-trained deep learning model to extract co-attentioned features and prediction results. Then, train and test the outlier detection model. Directly run `python3 outlier.py` will handle the data stored in `data/total`, which could be download from the BaiduYun.

1. Training process
+ Input
+ Output

1. Testing process
