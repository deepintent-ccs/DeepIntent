# DeepIntent
![](CWRU.png)
Implementation of the paper *DeepIntent: Deep Icon-Behavior Learning for Detecting Intention-Behavior Discrepancy in Mobile Apps*.

## Introduction
In this work, we focus on detecting the intention-behavior discrepancies of interactive UI widgets in Android apps, which express their intentions via texts or images and respond to users’ interactions (e.g., clicking a button). Specifically, we focus on the interactive UI widgets that use icons to indicate their expected behaviors, referred to as icon widgets, since icon widgets are prevalent in apps and many of them access sensitive information.

We propose to build a novel framework, DeepIntent, that learns an icon-behavior model from a large number of apps, and uses the model to detect undesired behaviors. The design of DeepIntent is based on three key insights. 
>First, mobile apps’ UIs are expected to be evident to users, and icons indicating the same type of sensitive behavior should have similar looks. 

>Second, in different UI contexts, icons may reflect different intentions. 

>Third, users expect certain behaviors when interacting with icon widgets that have specific looks, and undesired behaviors usually contradict users’ expectations.

To capture such general expectation, we propose to develop program analysis techniques that can associate icons to their sensitive behaviors, and apply the techniques to extract the associations from a corpus of popular apps to learn models on expected behaviors for icon widgets with specific looks. Such model can then be used to detect abnormal behaviors as intention-behavior discrepancies. In particular, we use permission uses to summarize icon widgets’ sensitive behaviors (i.e., sensitive APIs invoked), since undesired behaviors need to request permissions to access sensitive information.

Based on these key insights, DeepIntent provides a novel learning approach, deep icon-behavior learning, which consists of three major phases (Icon Widget Analysis, Learning Icon-Behavior Model, and Detecting Intention-Behavior Discrepancies)


We collect a set of 9,891 benign apps and 16,262 malicious apps, from which we extract over 10,000 icon widgets that are mapped to sensitive permission uses. We use 80% of the icons from the benign apps as training data, and detect the intention-behavior discrepancies on the remaining icons from the benign apps and all the icons from malicious apps. For the test set, we manually label whether there is an intention-behavior discrepancy to form the ground truth. Finally, DeepIntent returns a ranked list based on the outlier scores for detecting intention-behavior discrepancies.
## Requirements

## Repository Contents
The program analysis is in DeepIntent/IconWidgetAnalysis/Static_Analysis/ directory. It contains icon-widget-association analysis (i.e. GATOR, IconIntent), icon-behavior-association analysis (i.e. ic3, APKCallGraph), and icon-permisssion mapping. 
## Usage

## Citing

If you find DeepIntent useful in your research, we require you to cite the following paper:

```
@inproceedings{
	title = {DeepIntent: Deep Icon-Behavior Learning for Detecting Intention-Behavior Discrepancy in Mobile Apps},
	author = {Shengqu Xi, Shao Yang, Xusheng Xiao, Yuan Yao, Yayuan Xiong, Fengyuan Xu, Haoyu Wang, Peng Gao, Zhuotao Liu, Feng Xu, Jian Lu},
	booktitle = {2019 ACM SIGSAC Conference on Computer and Communications Security (CCS'19), November 11--15, 2019, London, United Kingdom},
	year = {2019},
}
```
