# -*- coding: UTF-8 -*-

import os
import pickle

import keras
import numpy as np
import PIL.Image as Image
from pyod.models.knn import KNN
from pyod.models.auto_encoder import AutoEncoder

import metrics
from layers import CoAttentionParallel


# =========================
# data maintenance
# =========================


def save_pkl_data(path, data):
    with open(path, 'wb') as fo:
        pickle.dump(data, fo)


def load_pkl_data(path):
    with open(path, 'rb') as fi:
        data = pickle.load(fi)
    return data


# =========================
# load model and data
# =========================


def load_model(path_model):
    model = keras.models.load_model(path_model, custom_objects={
        'CoAttentionParallel': CoAttentionParallel
    })
    extract_f = keras.models.Model(
        inputs=model.input, outputs=model.get_layer('feature').output
    )
    extract_p = model

    return extract_f, extract_p


def load_meta(path_model_meta):
    meta = load_pkl_data(path_model_meta)

    image_shape = meta['ModelConf'].img_shape
    re_sample_type = meta['ModelConf'].img_re_sample
    text_len = meta['ModelConf'].text_length
    id2label = {v: k for k, v in meta['label2id'].items()}
    permission_names = [id2label[i] for i in range(len(id2label))]

    return image_shape, re_sample_type, text_len, permission_names


# =========================
# prepare data
# =========================


def prepare_training_data(data, img_shape, re_sample_type, text_len, permission_names):
    img_size, img_channel = img_shape[:2], img_shape[-1]

    input_images, input_texts, permissions = [], [], []
    for img_data, tokens, permission_label in data:
        input_images.append(prepare_image(img_data, img_size, img_channel, re_sample_type))
        input_texts.append(prepare_text(tokens, text_len))
        permissions.append(prepare_permissions(permission_label, permission_names))

    input_images = np.array(input_images)
    input_texts = np.array(input_texts)
    inputs = [input_images, input_texts]

    return inputs, permissions


def prepare_testing_data(data, img_shape, re_sample_type, text_len, permission_names):
    data_like_training, outlier_labels = [], []
    for i in range(len(data)):  # img_data, tokens, permission_label, outlier_label
        data_like_training.append(data[i][:3])
        outlier_labels.append(data[i][-1])

    inputs, permissions = prepare_training_data(
        data_like_training, img_shape, re_sample_type, text_len, permission_names
    )

    return inputs, permissions, outlier_labels


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
    np_img = np.array(img) / 255.0

    return np_img


def image_decompress(img_mode, img_size, img):
    """Decompress the image.

    Given the compressed image tuple `temp`, the parameter could simply be `*temp`.

    :param img_mode:
        String, image's color mode, such as RGB.
    :param img_size:
        Tuple of int, the size of image, that is (width, height).
    :param img:
        The compressed image data.

    :return:
        image: Image object, which contains image binaries.
    """
    img = Image.frombytes(img_mode, img_size, img)

    return img


def prepare_text(words, max_len):
    if len(words) < max_len:
        words = words + [0] * (max_len - len(words))
    else:
        words = words[:max_len]
    return np.array(words)


def prepare_permissions(label, permission_names):
    return {permission_names[p] for p in label}


# =========================
# outlier detection
# =========================


def weight_knn(knn, x, xs, k=5):
    # get the k nearest neighbors of the current point
    neighbors = get_k_nearest_neighbors(knn, x, xs, k)

    # compute distance
    dist = []
    if len(neighbors) == 0:
        # no neighbor, return the default weight
        dist.append(1.0)
    elif len(neighbors) == 1:
        # only one neighbor
        # compute distance between the current point and neighbor
        dist.append(distance_euclidean(x, neighbors[0]))
    else:
        # more than one different neighbor
        # compute distance between each others
        for i in range(len(neighbors) - 1):
            for j in range(i + 1, len(neighbors)):
                x0, x1 = neighbors[i], neighbors[j]
                dist.append(distance_euclidean(x0, x1))

    # average and inverse
    dist = sum(dist) / len(dist)
    return 1.0 / dist


def get_k_nearest_neighbors(knn, x, xs, k):
    neighbors_by_dist = {}  # {dist: [np.array, ... ]}

    k_query = k
    while sum([len(vs) for vs in neighbors_by_dist.values()]) < k:
        dist_arr, indexes = knn.tree_.query([x], k=k_query)
        neighbors = [xs[i] for i in indexes[0]]

        for n in neighbors:
            dist = distance_euclidean(x, n)
            if dist not in neighbors_by_dist:
                neighbors_by_dist[dist] = []
                neighbors_by_dist[dist].append(n)
            else:
                # check and remove same neighbor
                is_unique = True
                for n2 in neighbors_by_dist[dist]:
                    if np.all(n == n2):
                        is_unique = False
                        break
                if is_unique:
                    neighbors_by_dist[dist].append(n)

        k_query *= 2
        k_query = len(xs) if k_query >= len(xs) else k_query

    neighbors = []
    sorted_neighbors = sorted(neighbors_by_dist.items(), key=lambda item: item[0])
    for dist, vectors in sorted_neighbors:
        neighbors.extend(vectors)
    return neighbors


def distance_euclidean(x0, x1):
    import math
    assert len(x0) == len(x1)

    d = 0.0
    for k in range(len(x0)):
        d += (x0[k] - x1[k]) * (x0[k] - x1[k])
    d = math.sqrt(d)

    return d


def weight_combine(ws1, ws2):
    assert len(ws1) == len(ws2)
    cws = []  # combined weights
    for i in range(len(ws1)):
        ws = []
        for w1, w2 in zip(ws1[i], ws2[i]):
            ws.append(w1 * 0.5 + w2 * 0.5)
        cws.append(ws)
    return cws


def training(path_data, img_shape, re_sample_type, text_len, permission_names, extract_f):
    # load training data
    print('loading training data')
    data = load_pkl_data(path_data)  # img_data, tokens, perms
    inputs, permissions = prepare_training_data(
        data, img_shape, re_sample_type, text_len, permission_names
    )

    # get features
    print('generating training features')
    features = extract_f.predict(inputs)

    # train auto encoder model, knn model
    print('training outlier model + knn model')
    detectors = []
    knn_trees = []
    features_in_permissions = []  # features in each permission, [permission_id, feature_id]
    for p in permission_names:
        print('training', p, '...')
        features_current = []
        for i in range(len(permissions)):
            if p in permissions[i]:
                features_current.append(features[i])
        features_in_permissions.append(features_current)

        detector = AutoEncoder(epochs=200, verbose=0)
        detector.fit(features_current)
        detectors.append(detector)

        knn = KNN()
        knn.fit(features_current)
        knn_trees.append(knn)

    return detectors, knn_trees, features_in_permissions


def testing(path_data, img_shape, re_sample_type, text_len, permission_names,
            extract_f, extract_p, detectors, knn_trees, features_in_labels):
    # load testing data
    data = load_pkl_data(path_data)  # img_data, tokens, perms, outlier_labels
    inputs, permissions, outlier_labels = prepare_testing_data(
        data, img_shape, re_sample_type, text_len, permission_names
    )
    # prediction weights
    prediction_results = extract_p.predict(inputs).tolist()
    prediction_weights = [[1.0 - w for w in ws] for ws in prediction_results]
    # get features
    features = extract_f.predict(inputs)
    # outlier score, knn weight
    scores = []
    neighbour_weights = []
    for i in range(len(features)):
        score, nw = [], []
        for p_index in range(len(permission_names)):
            p_name = permission_names[p_index]
            if p_name in permissions[i]:
                score.append(detectors[p_index].decision_function([features[i]])[0])
            else:
                score.append(0.0)
            nw.append(weight_knn(knn_trees[p_index], features[i], features_in_labels[p_index]))
        scores.append(score)
        neighbour_weights.append(nw)

    return scores, neighbour_weights, prediction_weights, outlier_labels


def total_example():
    path_current = os.path.dirname(os.path.abspath(__file__))
    path_data = os.path.join(path_current, '..', 'data', 'total')

    # load meta data
    path_meta = os.path.join(path_data, 'deepintent.meta')
    image_shape, re_sample_type, text_len, permission_names = load_meta(
        path_meta
    )

    # load co-attention model
    print('loading model')
    path_model = os.path.join(path_data, 'deepintent.model')
    extract_f, extract_p = load_model(path_model)

    # training
    path_training = os.path.join(path_data, 'training.save.pkl')
    detectors, knn_trees, features_in_permissions = training(
        path_training, image_shape, re_sample_type, text_len, permission_names, extract_f
    )

    # testing and evaluation
    # benign
    print('testing benign')
    path_testing_benign = os.path.join(path_data, 'benign.save.pkl')
    scores_teb, nws_teb, pws_teb, labels_teb = testing(
        path_testing_benign, image_shape, re_sample_type, text_len, permission_names,
        extract_f, extract_p, detectors, knn_trees, features_in_permissions
    )
    print('evaluate benign')
    cws_teb = weight_combine(nws_teb, pws_teb)
    scores_combine_teb = [[w * s for w, s in zip(ws, ss)] for ws, ss in zip(cws_teb, scores_teb)]
    metrics.metric_permission_based_outlier(scores_combine_teb, labels_teb, permission_names)
    metrics.metric_overall_outlier(scores_teb, cws_teb, labels_teb)

    # malicious
    print('testing malicious')
    path_testing_malicious = os.path.join(path_data, 'malicious.save.pkl')
    scores_tem, nws_tem, pws_tem, labels_tem = testing(
        path_testing_malicious, image_shape, re_sample_type, text_len, permission_names,
        extract_f, extract_p, detectors, knn_trees, features_in_permissions
    )
    print('evaluate malicious')
    cws_tem = weight_combine(nws_tem, pws_tem)
    scores_combine_tem = [[w * s for w, s in zip(ws, ss)] for ws, ss in zip(cws_tem, scores_tem)]
    metrics.metric_permission_based_outlier(scores_combine_tem, labels_tem, permission_names)
    metrics.metric_overall_outlier(scores_tem, cws_tem, labels_tem)


def main():
    total_example()


if __name__ == '__main__':
    main()
