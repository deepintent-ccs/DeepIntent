# -*- coding: UTF-8 -*-

import os
import codecs
import pickle

import keras
import numpy as np
import PIL.Image as Image
from pyod.models.knn import KNN
from pyod.models.auto_encoder import AutoEncoder

from layers import CoAttentionParallel

IMG_SHAPE = (128, 128, 4)
TEXT_MAX_LEN = 20


def save_pkl_data(fp, data):
    with open(fp, "wb") as fo:
        pickle.dump(data, fo)


def load_pkl_data(fp):
    with open(fp, "rb") as fi:
        data = pickle.load(fi)
    return data


def load_model(fp_model):
    model = keras.models.load_model(fp_model, custom_objects={
        "CoAttentionParallel": CoAttentionParallel
    })
    # print(model.summary())
    extract_f = keras.models.Model(
        inputs=model.input, outputs=model.get_layer("co_attention_para_1").output
    )
    extract_p = model

    return extract_f, extract_p


def prepare_image(img, target_image_shape=IMG_SHAPE):
    target_image_size = target_image_shape[:2]
    target_image_mode = "RGBA" if target_image_shape[-1] == 4 else "RGB"
    img = img.resize(target_image_size, Image.BILINEAR)
    img = img.convert(target_image_mode)

    np_img = np.array(img) / 255.0
    return np_img


def prepare_text(words, w2id, max_len=TEXT_MAX_LEN):
    words = [w2id.get(w, 1) for w in words]
    if len(words) < TEXT_MAX_LEN:
        words = words + [0] * (max_len - len(words))
    else:
        words = words[:max_len]
    return np.array(words)


def load_data(fd_data, meta=None):
    # load meta data
    fp_meta = os.path.join(fd_data, "meta.txt")
    if meta is None:
        meta = []
        assert os.path.exists(fp_meta)
        with codecs.open(fp_meta, encoding="UTF-8", mode="r") as fi:
            meta.append(eval(fi.readline()))  # label2id
            meta.append(eval(fi.readline()))  # word2id

    # load readable data
    data = []
    with codecs.open(os.path.join(fd_data, "data.txt"), encoding="UTF-8", mode="r") as fi:
        for line in fi:
            current = eval(line)
            # tr: i, perms, tokens
            # te: i, perms, marks, tokens
            data.append(current[:-1] + [prepare_text(current[-1], meta[1])])

    # load image
    images = []
    for fn_img in os.listdir(os.path.join(fd_data, "images")):
        fp_img = os.path.join(fd_data, "images", fn_img)
        img = Image.open(fp_img)
        images.append(prepare_image(img))

    # tidy
    info, labels = [], []
    input_images, input_texts = [], []
    for i in range(len(data)):
        info.append(data[i][:-1])
        labels.append(data[i][3])
        input_images.append(images[i])
        input_texts.append(data[i][-1])
    inputs = [input_images, input_texts]

    return inputs, labels, info, meta


def extract_marks(info, labels, target_labels):
    marks = [d[-1] for d in info]
    info = [d[:-1] for d in info]

    marks_new = []
    for i in range(len(labels)):
        m = ["-"] * len(target_labels)
        for j in range(len(labels[i])):
            label = labels[i][j]
            m[target_labels.index(label)] = marks[i][j]
        marks_new.append(m)

    return marks_new, info


def weight_knn(knn, x, xs, k=5):
    import math
    # get the k nearest neighbors of the current point
    dist_arr, indexes = knn.tree_.query([x], k=k)
    neighbors = [xs[i] for i in indexes[0]]

    dist = []
    for i in range(len(neighbors) - 1):
        for j in range(i + 1, len(neighbors)):
            x0, x1 = neighbors[i], neighbors[j]

            d = 0.0
            for k in range(len(x0)):
                d += (x0[k] - x1[k]) * (x0[k] - x1[k])
            dist.append(math.sqrt(d))
    dist = sum(dist) / len(dist)
    # dist = np.mean(dist_arr, axis=1)[-1]

    return 1.0 / dist


def weight_combine(ws1, ws2):
    assert len(ws1) == len(ws2)
    cws = []  # combined weights
    for i in range(len(ws1)):
        ws = []
        for w1, w2 in zip(ws1[i], ws2[i]):
            ws.append(w1 * 0.5 + w2 * 0.5)
        cws.append(ws)
    return cws


def save_plt(fp_save):
    import matplotlib.pyplot as plt

    if fp_save.endswith(".png"):
        plt.savefig(fp_save)
    elif fp_save.endswith(".pdf"):
        plt.savefig(fp_save, dpi=600, format="pdf")
    plt.close("all")


def plot_curve(title, title_y, x, y, fp_save=None):
    import matplotlib.pyplot as plt
    # from pylab import mpl
    # mpl.rcParams['font.sans-serif'] = ['SimHei']  # 用来显示中文，不然会乱码

    plt.plot(x, y)
    plt.title(title)
    plt.xlabel("Top-K")
    plt.ylabel(title_y)

    if fp_save is None:
        plt.show()
    else:
        save_plt(fp_save)


def plot_precision_recall(title, x, py, ry, fp_save=None):
    import matplotlib.pyplot as plt

    plt.title(title)
    plt.xlabel("Top-K")
    plt.ylabel("Precision/Recall")
    plt.plot(x, py, label="Precision")
    plt.plot(x, ry, label="Recall")
    plt.legend()

    if fp_save is None:
        plt.show()
    else:
        save_plt(fp_save)


def metric_permission_based_outlier(scores, marks, target_labels, title=None):
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    for i in range(len(target_labels)):
        label_i = target_labels[i]

        scores_i, y_true = [], []
        for j in range(len(scores)):
            if marks[j][i] != "-":
                scores_i.append(scores[j][i])
                y_true.append(1 if marks[j][i] == "n" else 0)

        pk, rk = [], []
        for k in range(1, len(y_true)):
            y_predict = get_label_n(y_true, scores_i, k)
            pk.append(precision_score(y_true, y_predict))
            rk.append(recall_score(y_true, y_predict))

        n = sum(y_true) - 1
        if 0 <= n < len(pk):
            # print(y_true)
            # print(scores_i)
            print("{}@{}/{}".format(label_i, n, len(scores_i)), pk[n], rk[n], roc_auc_score(y_true, scores_i))
        else:
            print("{}@{}/{}".format(label_i, n, len(scores_i)), 0.0, 0.0, 0.0)

        if title is not None:
            fp_save = os.path.join("results_weighted", title)
            plot_curve("{}_{}_precision".format(title, label_i), "precision", list(range(1, len(y_true))), pk,
                       fp_save=fp_save + "_{}_precision.pdf".format(label_i))
            plot_curve("{}_{}_recall".format(title, label_i), "recall", list(range(1, len(y_true))), rk,
                       fp_save=fp_save + "_{}_recall.pdf".format(label_i))


def metric_overall_outlier(scores, weights, marks, title=None):
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    y_true = []
    weighted_scores = []
    for i in range(len(scores)):
        score = 0.0
        count_no_zero = 0
        for w, s, m in zip(weights[i], scores[i], marks[i]):
            if m != "-":
                score += w * s
                count_no_zero += 1
        if count_no_zero > 0:
            score = score / count_no_zero

        # print(1 if "n" in marks[i] else 0, score, scores[i], weights[i], marks[i])
        weighted_scores.append(score)
        y_true.append(1 if "n" in marks[i] else 0)

    pk, rk = [], []
    for k in range(1, len(y_true)):
        y_predict = get_label_n(y_true, weighted_scores, k)
        pk.append(precision_score(y_true, y_predict))
        rk.append(recall_score(y_true, y_predict))
    n = sum(y_true)
    print("overall@{}".format(n), len(y_true), pk[n], rk[n], roc_auc_score(y_true, weighted_scores))

    if title is not None:
        fp_save = os.path.join("results", "overall_" + title)
        # plot_curve("overall_{}_precision".format(title), "precision", list(range(1, len(y_true))), pk,
        #            fp_save=fp_save + "_precision.pdf")
        # plot_curve("overall_{}_recall".format(title), "recall", list(range(1, len(y_true))), rk,
        #            fp_save=fp_save + "_recall.pdf")
        plot_precision_recall(
            "", list(range(1, len(y_true))), pk, rk, fp_save=fp_save+".pdf"
        )


def training(fd_data, target_labels, extract_f):
    # load training data
    print("loading training data")
    inputs_tr, labels_tr, info_tr, meta = load_data(fd_data)

    # get features
    print("generating training features")
    f_tr = extract_f.predict(inputs_tr)

    # train auto encoder model, knn model
    print("training outlier model + knn model")
    knn_trees = []
    detectors = []
    f_labels = []
    for label in target_labels:
        print("training", label, "...")
        f_label = []
        for i in range(len(labels_tr)):
            if label in labels_tr[i]:
                f_label.append(f_tr[i])
        f_labels.append(f_label)

        detector = AutoEncoder(epochs=200, verbose=0)
        detector.fit(f_label)
        detectors.append(detector)

        knn = KNN()
        knn.fit(f_label)
        knn_trees.append(knn)

    return detectors, knn_trees, meta, f_labels


def testing(fd_data, meta, target_labels, extract_f, extract_p, detectors, knn_trees, f_labels):
    # load testing data
    inputs, labels, info, _ = load_data(fd_data, meta=meta)  # teb -> test benign
    marks, info = extract_marks(info, labels, target_labels)
    # get features
    fs = extract_f.predict(inputs)
    # permission based outlier
    # outlier score, knn weight, prediction weight
    prediction_weights = extract_p.predict(inputs).tolist()
    scores = []
    neighbour_weights = []
    for i in range(len(fs)):
        score, nw = [], []
        for j in range(len(target_labels)):
            label = target_labels[j]
            if label in labels[i]:
                score.append(detectors[j].decision_function([fs[i]])[0])
            else:
                score.append(0.0)
            nw.append(weight_knn(knn_trees[j], fs[i], f_labels[j]))
        scores.append(score)
        neighbour_weights.append(nw)

    return marks, scores, neighbour_weights, prediction_weights


def main():
    fd_data = os.path.join("data", "autoencoder")
    target_labels = ["NETWORK", "LOCATION", "MICROPHONE", "SMS", "CAMERA", "CALL", "STORAGE", "CONTACTS"]

    fd_testing_benign = os.path.join(fd_data, "testing", "benign")
    fp_teb = os.path.join(fd_testing_benign, "results.pkl")
    fd_testing_malicious = os.path.join(fd_data, "testing", "malicious")
    fp_tem = os.path.join(fd_testing_malicious, "results.pkl")

    # load co-attention model
    print("loading model")
    fp_model = os.path.join(fd_data, "model.h5")
    extract_f, extract_p = load_model(fp_model)

    # training
    fd_training = os.path.join(fd_data, "training")
    detectors, knn_trees, meta, f_labels = training(fd_training, target_labels, extract_f)

    # testing
    print("testing benign")
    marks_teb, scores_teb, nws_teb, pws_teb = testing(
        fd_testing_benign, meta, target_labels, extract_f, extract_p, detectors, knn_trees, f_labels
    )
    save_pkl_data(fp_teb, [marks_teb, scores_teb, nws_teb, pws_teb])
    print("testing malicious")
    marks_tem, scores_tem, nws_tem, pws_tem = testing(
        fd_testing_malicious, meta, target_labels, extract_f, extract_p, detectors, knn_trees, f_labels
    )
    save_pkl_data(fp_tem, [marks_tem, scores_tem, nws_tem, pws_tem])

    # evaluation
    print()
    print("evaluate benign")
    marks_teb, scores_teb, nws_teb, pws_teb = load_pkl_data(fp_teb)
    cws_teb = weight_combine(nws_teb, [[1.0 - w for w in ws] for ws in pws_teb])
    scores_combine_teb = [[w * s for w, s in zip(ws, ss)] for ws, ss in zip(cws_teb, scores_teb)]
    metric_permission_based_outlier(scores_combine_teb, marks_teb, target_labels)
    metric_overall_outlier(scores_teb, cws_teb, marks_teb, None)

    print()
    print("evaluate malicious")
    marks_tem, scores_tem, nws_tem, pws_tem = load_pkl_data(fp_tem)
    cws_tem = weight_combine(nws_tem, [[1.0 - w for w in ws] for ws in pws_tem])
    scores_combine_tem = [[w * s for w, s in zip(ws, ss)] for ws, ss in zip(cws_tem, scores_tem)]
    metric_permission_based_outlier(scores_combine_tem, marks_tem, target_labels)
    metric_overall_outlier(scores_tem, cws_tem, marks_tem, None)


if __name__ == "__main__":
    main()
