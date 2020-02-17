# -*- coding: UTF-8 -*-

"""Input data preparation.

The input preparation phase will transform the data into the model's
required format, such as, splitting data (train, valid, test set),
padding texts, and transforming permission into one-hot format.
"""

import numpy as np

from tools import image_decompress


def prepare_data(data, image_shape, re_sample_type, text_len,
                 label_num, train_ratio, valid_ratio):
    import random
    from keras.preprocessing.sequence import pad_sequences

    # random shuffle the data
    random.shuffle(data)

    # prepare the data
    image_size, image_channel = image_shape[:2], image_shape[-1]
    texts, images, labels = [], [], []
    for img_data, tokens, perms in data:
        images.append(prepare_image(img_data, image_size, image_channel, re_sample_type))
        texts.append(tokens)
        labels.append(prepare_multi_label_list(perms, label_num))

    # transform the data into numpy format
    texts = pad_sequences(texts, padding='post', truncating='post', maxlen=text_len)
    images = np.array(images)
    labels = np.array(labels)

    # split the data
    data_train, data_valid, data_test = prepare_split_data(
        [images, texts, labels], train_ratio, valid_ratio
    )

    data_train = prepare_combine_inputs(data_train)
    data_valid = prepare_combine_inputs(data_valid)
    data_test = prepare_combine_inputs(data_test)

    return data_train, data_valid, data_test


def prepare_image(img_data, target_size, target_channel, re_sample_type):
    # resize the image
    img = image_decompress(*img_data)
    img = img.resize(target_size, re_sample_type)

    # convert image
    if target_channel == 4:
        img = img.convert('RGBA')
    elif target_channel == 3:
        img = img.convert('RGB')
    elif target_channel == 1:
        img = img.convert('L')

    # transform to 0.0 to 1.0 values
    img = np.array(img) / 255.0

    return img


def prepare_multi_label_list(target, label_nums):
    return [
        1.0 if i in target else 0.0 for i in range(label_nums)
    ]


def prepare_split_data(data, train_ratio, valid_ratio):
    import math

    pivot_train = math.floor(len(data[0]) * train_ratio)
    pivot_valid = pivot_train + math.floor(len(data[0]) * valid_ratio)

    data_train = [d[:pivot_train] for d in data]
    data_valid = [d[pivot_train: pivot_valid] for d in data]
    data_test = [d[pivot_valid:] for d in data]

    return data_train, data_valid, data_test


def prepare_combine_inputs(data):
    data_inputs = data[:-1]
    data_labels = data[-1]
    return [data_inputs, data_labels]
