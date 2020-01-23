# -*- coding: UTF-8 -*-

# =================
# metrics
# =================


def precision_ex_single(yt, yp):
    """Example based multi-label precision in a single data."""
    tp, p = 0, 0
    for y0, y1 in zip(yt, yp):
        if y1 == 1:
            p += 1
            tp += 1 if y0 == 1 else 0

    return float(tp) / float(p) if p != 0 else 0.0


def recall_ex_single(yt, yp):
    """Example based multi-label recall in a single data."""
    tp, t = 0, 0
    for y0, y1 in zip(yt, yp):
        if y0 == 1:
            t += 1
            tp += 1 if y1 == 1 else 0

    return float(tp) / float(t) if t != 0 else 0.0


def precision_ex(y_true, y_predict):
    score = 0.0
    for yt, yp in zip(y_true, y_predict):
        score += precision_ex_single(yt, yp)

    return score / len(y_true) if len(y_true) != 0 else 0.0


def recall_ex(y_true, y_predict):
    score = 0.0
    for yt, yp in zip(y_true, y_predict):
        score += recall_ex_single(yt, yp)

    return score / len(y_true) if len(y_true) != 0 else 0.0


def metrics_ex(y_true, y_predict):
    import numpy as np
    from sklearn.metrics import accuracy_score

    precision = precision_ex(y_true, y_predict)
    recall = recall_ex(y_true, y_predict)
    f1 = 2 * precision * recall / (precision + recall)
    acc = accuracy_score(np.array(y_true), np.array(y_predict))

    return precision, recall, f1, acc


def accuracy_l(y_true, y_predict):
    import numpy as np
    from sklearn.metrics import accuracy_score

    if len(y_true) == 0:
        return 0.0

    result = []
    for i in range(len(y_true[0])):
        y_true_i = [y[i] for y in y_true]
        y_predict_i = [y[i] for y in y_predict]

        acc = accuracy_score(np.array(y_true_i), np.array(y_predict_i))
        result.append(acc)

    return result


def metrics_l(y_true, y_predict):
    import numpy as np
    from sklearn.metrics import precision_recall_fscore_support

    results = precision_recall_fscore_support(np.array(y_true), np.array(y_predict))
    precision, recall, f1, support = results
    acc = accuracy_l(y_true, y_predict)

    return precision, recall, f1, acc, support


def evaluate(y_true, y_predict, predict_threshold, n=-1):
    import numpy as np

    # simple assertion
    assert len(y_true) == len(y_predict)
    assert len(y_true) > 0
    assert len(y_true[0]) > 0

    # prepare predictions
    y_predict_th = [[1 if y > predict_threshold else 0 for y in ys] for ys in y_predict]
    y_sort = [np.argsort(ys)[::-1] for ys in y_predict]
    n = len(y_true[0]) if n < 0 else n

    # threshold based metrics
    results_th_ex = metrics_ex(y_true, y_predict_th)  # precision_ex, recall_ex, f1_ex, acc_ex
    results_th_l = metrics_l(y_true, y_predict_th)    # precision_l, recall_l, f1_l, acc_l, support_l

    # k based metrics
    results_k_ex, results_k_l = [], []
    for k in range(n):
        y_predict_k = [[1 if j in ys[:k + 1] else 0 for j in range(len(ys))] for ys in y_sort]
        results_k_ex.append(metrics_ex(y_true, y_predict_k))
        results_k_l.append(metrics_l(y_true, y_predict_k))

    return results_th_ex, results_th_l, results_k_ex, results_k_l


def compute_mean_var(scores):
    import numpy as np

    def mean_var(scores_template):
        return np.concatenate([
            np.expand_dims(np.mean(scores_template, -1), -1),
            np.expand_dims(np.var(scores_template, -1), -1),
        ], -1)

    scores_th_ex = [s[0] for s in scores]  # (n, m)
    scores_th_l = [s[1] for s in scores]   # (n, l, m)
    scores_k_ex = [s[2] for s in scores]   # (n, k, m)
    scores_k_l = [s[3] for s in scores]    # (n, k, l, m)

    scores_th_ex = np.transpose(np.array(scores_th_ex), (1, 0))    # (m, n)
    scores_th_l = np.transpose(np.array(scores_th_l), (1, 2, 0))   # (l, m, n)
    scores_k_ex = np.transpose(np.array(scores_k_ex), (1, 2, 0))   # (k, m, n)
    scores_k_l = np.transpose(np.array(scores_k_l), (1, 2, 3, 0))  # (k, l, m, n)

    avg_th_ex = mean_var(scores_th_ex)  # (m, [mean, var])
    avg_th_l = mean_var(scores_th_l)    # (l, m, [mean, var])
    avg_k_ex = mean_var(scores_k_ex)    # (k, m, [mean, var])
    avg_k_l = mean_var(scores_k_l)      # (k, l, m, [mean, var])

    return avg_th_ex, avg_th_l, avg_k_ex, avg_k_l


# =================
# display
# =================
def display_scores_ex(scores, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    for i, name in enumerate(["precision", "recall", "f1", "acc"]):
        print("{}: [{:.4f}]".format(name, scores[i]), end=" ", file=fo)
    print(file=fo)


def display_scores_ex_k(scores_k, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    for k in range(len(scores_k)):
        print("top@{}".format(k + 1), end=" ", file=fo)
        display_scores_ex(scores_k[k], fo)


def display_scores_l(scores, names, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    # print label names
    print("{:10}".format("LABELS"), end=" ", file=fo)
    for name in names:
        print("{:>10}".format(name), end=" ", file=fo)
    print(file=fo)

    for i, name in enumerate(["precision", "recall", "f1", "acc", "support"]):
        print("{:10}".format(name), end=" ", file=fo)
        for s in scores[i]:
            s = "[{:.4f}]".format(s) if isinstance(s, float) else "[{:6}]".format(s)
            print("{:>10}".format(s), end=" ", file=fo)
        print(file=fo)


def display_scores_l_k(scores_k, names, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    # print label names
    print("{:12}".format("LABELS"), end=" ", file=fo)
    for name in names:
        print("{:>10}".format(name), end=" ", file=fo)
    print(file=fo)

    # print by each metrics
    for i, name in enumerate(["precision", "recall", "f1", "acc"]):
        for k in range(len(scores_k)):
            name_k = "{}@{}".format(name, k + 1)
            print("{:12}".format(name_k), end=" ", file=fo)
            for s in scores_k[k][i]:
                s = "[{:.4f}]".format(s)
                print("{:>10}".format(s), end=" ", file=fo)
            print(file=fo)
    # print support
    print("{:12}".format("support@all"), end=" ", file=fo)
    for s in scores_k[0][-1]:
        s = "[{:6}]".format(s)
        print("{:>10}".format(s), end=" ", file=fo)
    print(file=fo)


def display_scores(scores, names, fo=None, is_k_print=False):
    import sys
    fo = sys.stdout if fo is None else fo

    scores_th_ex, scores_th_l, scores_k_ex, scores_k_l = scores
    print("## Example Based Scores - Threshold", file=fo)
    display_scores_ex(scores_th_ex, fo)
    if is_k_print:
        print("## Example Based Scores - Top K", file=fo)
        display_scores_ex_k(scores_k_ex, fo)
    print("## Label Based Scores - Threshold", file=fo)
    display_scores_l(scores_th_l, names, fo)
    if is_k_print:
        print("## Label Based Scores - Top K", file=fo)
        display_scores_l_k(scores_k_l, names, fo)


def display_avg_scores_ex(scores, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    for i, name in enumerate(["precision", "recall", "f1", "acc"]):
        print("{}: [{:.4f}][{:.4e}]".format(name, scores[i][0], scores[i][1]), end=" ", file=fo)
    print(file=fo)


def display_avg_scores_ex_k(scores_k, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    for k in range(len(scores_k)):
        print("top@{}".format(k + 1), end=" ", file=fo)
        display_avg_scores_ex(scores_k[k], fo)


def display_avg_scores_l(scores, names, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    # print label names
    print("{:10}".format("LABELS"), end=" ", file=fo)
    for name in names:
        print("{:^20}".format(name), end=" ", file=fo)
    print(file=fo)

    for i, name in enumerate(["precision", "recall", "f1", "acc", "support"]):
        print("{:10}".format(name), end=" ", file=fo)
        for s in scores[i]:
            s = list(s)
            s0 = "[{:.4f}]".format(s[0]) if s[0] <= 1.0 else "[{:.2f}]".format(s[0])
            s1 = "[{:.2e}]".format(s[1])
            print("{:>10}{:<10}".format(s0, s1), end=" ", file=fo)
        print(file=fo)


def display_avg_scores_l_k(scores_k, names, fo=None):
    import sys
    fo = sys.stdout if fo is None else fo

    # print label names
    print("{:12}".format("LABELS"), end=" ", file=fo)
    for name in names:
        print("{:^20}".format(name), end=" ", file=fo)
    print(file=fo)

    # print by each metrics
    for i, name in enumerate(["precision", "recall", "f1", "acc"]):
        for k in range(len(scores_k)):
            name_k = "{}@{}".format(name, k + 1)
            print("{:12}".format(name_k), end=" ", file=fo)
            for s in scores_k[k][i]:
                s0 = "[{:.4f}]".format(s[0]) if s[0] <= 1.0 else "[{:.2f}]".format(s[0])
                s1 = "[{:.2e}]".format(s[1])
                print("{:>10}{:<10}".format(s0, s1), end=" ", file=fo)
            print(file=fo)
    # print support
    print("{:12}".format("support@all"), end=" ", file=fo)
    for s in scores_k[0][-1]:
        s0 = "[{:.4f}]".format(s[0]) if s[0] <= 1.0 else "[{:.2f}]".format(s[0])
        s1 = "[{:.2e}]".format(s[1])
        print("{:>10}{:<10}".format(s0, s1), end=" ", file=fo)
    print(file=fo)


def display_average_scores(scores, names, n, fo=None, is_k_print=False):
    import sys
    fo = sys.stdout if fo is None else fo

    avg_th_ex, avg_th_l, avg_k_ex, avg_k_l = scores
    print("## Example Based Scores - Average@{} - Threshold".format(n), file=fo)
    display_avg_scores_ex(avg_th_ex, fo)
    if is_k_print:
        print("## Example Based Scores - Average@{} - Top K".format(n), file=fo)
        display_avg_scores_ex_k(avg_k_ex, fo)
    print("## Label Based Scores - Average@{} - Threshold".format(n), file=fo)
    display_avg_scores_l(avg_th_l, names, fo)
    if is_k_print:
        print("## Label Based Scores - Average@{} - Top K".format(n), file=fo)
        display_avg_scores_l_k(avg_k_l, names, fo)


def display_predictions(results_name, raw_data, x_test, y_true, y_predict, id2vocab, id2label):
    import os
    import codecs
    import numpy as np
    from PIL import Image

    # create new folder
    fd_output = ''  # os.path.join(fd_results, "{}".format(results_name))
    # safe_create(fd_output, replace=True)

    # create txt file
    fd_output_txt = os.path.join(fd_output, "results.txt")
    fo = codecs.open(fd_output_txt, mode="w", encoding="UTF-8")

    # loop test data
    y_predict_th = [[1 if y > 0.5 else 0 for y in ys] for ys in y_predict]
    for i in range(len(y_true)):
        # prediction result
        y_t, y_p = list(y_true[i]), list(y_predict_th[i])
        r = "T" if y_t == y_p else "F"

        # meta info
        x_image, x_text = x_test[0][i], x_test[1][i]
        x_text = [id2vocab[t] for t in x_text if t != 0]
        y_t = [id2label[j] for j, y in enumerate(y_t) if y != 0]
        y_p = [id2label[j] for j, y in enumerate(y_p) if y != 0]
        app_name, img_name = raw_data[i][0], raw_data[i][1]
        print(i, r, app_name, img_name, y_t, y_p, x_text, file=fo)

        # input image
        img = np.array(x_image, dtype=np.float)
        img = np.array(img * 255, dtype=np.uint8)
        img = Image.fromarray(img)
        fp_img = os.path.join(fd_output, "{}_{}.png".format(i, r))
        img.save(fp_img)

        # raw image
        compressed_rgb = raw_data[i][-2]
        img_mode, img_size, img_data = compressed_rgb
        img = Image.frombytes(img_mode, img_size, img_data)
        fp_img = os.path.join(fd_output, "{}_{}_raw.png".format(i, r))
        img.save(fp_img)

    fo.close()
