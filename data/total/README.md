# Total data set

This is the total data set used in our experiments, contains pre-processed `<icon, texts, permissions>` triples and manually marked outliers. The corresponding program will start from training the deep learning model and then detect intention behavior discrepancies. Please download the total data set from [BaiduYun](https://pan.baidu.com/s/1E0iE-Nm8xx4qsFB6PnkYwA) (password: *nmqf*) or [Dropbox](https://www.dropbox.com/sh/0ba30ay99ora50b/AACG-nszNCGEDs9fDor75JI3a?dl=0), and put the contains in current folder.

## Structure

+ permission_benign.csv.zip
Program analysis results of all benign apps. 

+ permission_malicious.csv.zip
Program analysis results of all malicious apps.

+ benign.total.pkl
All the `<icon, texts, permissions>` triples extracted from benign apps.
Each row is in `[img_data, texts, permissions]` format.

+ benign.test.x.pkl
Split testing data, *x* means testing index, the provided model is trained with `x=0`.

+ deepintent.model
The trained model, is trained with index 0.

+ deepintent.meta
The meta data of the trained model, contains vocabulary, permission names, and other configurations.

+ outlier.training.pkl
Data used to learn the outlier model.
Each data is the same as model's training data, in `[img_data, texts, permissions]` format.
Note that, intention behavior discrepancy is an unsupervised process, and they are only selected from benign apps but do not have outlier labels.

+ outlier.testing.benign.pkl
Manually labeled benign icons.
It contains outlier labels of each permission, outlier values could be 1 (outlier), 0 (not used such permission), -1 (inlier).
Each row is in `[img_data, texts, permissions]` format.

+ outlier.testing.malicious.pkl
Manually labeled malicious icons.
It contains outlier labels of each permission, outlier values could be 1 (outlier), 0 (not used such permission), -1 (inlier).
Each row is in `[img_data, texts, permissions]` format.

## How to use

### Training the model

Move to `IconBehaviorLearning`, then run `python3 train.py`.
The program will split `benign.total.pkl` and train the model 3 times.
Note that, each time the data will be shuffle by default.

### Predicting with trained model

Move to `IconBehaviorLearning`, then run `python3 predict.py`.
The program will load `deepintent.model` and `deepintent.meta`.
Then use `benign.test.x.pkl` to test, which is the real split testing data of the trained model.

### Outlier detection

Move to `IntentionBehaviorDiscrepancy`, then run `python3 outlier.py`.
The program will use `deepintent.model` and `deepintent.meta` to get co-attentioned features and prediction vectors.
Then it uses `outlier.training.pkl` to learn the outlier model.
Finally, it uses `outlier.testing.benign.pkl` and `outlier.testing.malicious.pkl` to evaluate the results.
