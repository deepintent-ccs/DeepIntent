# -*- coding: UTF-8 -*-

import os


# =========================
# plot
# =========================


def save_plt(path_save):
    import matplotlib.pyplot as plt

    if path_save.endswith('.png'):
        plt.savefig(path_save)
    elif path_save.endswith('.pdf'):
        plt.savefig(path_save, dpi=600, format='pdf')
    plt.close('all')


def plot_curve(title, title_y, x, y, path_save=None):
    import matplotlib.pyplot as plt

    plt.plot(x, y)
    plt.title(title)
    plt.xlabel('Top-K')
    plt.ylabel(title_y)

    if path_save is None:
        plt.show()
    else:
        save_plt(path_save)


def plot_precision_recall(title, x, py, ry, path_save=None):
    import matplotlib.pyplot as plt

    plt.title(title)
    plt.xlabel('Top-K')
    plt.ylabel('Precision/Recall')
    plt.plot(x, py, label='Precision')
    plt.plot(x, ry, label='Recall')
    plt.legend()

    if path_save is None:
        plt.show()
    else:
        save_plt(path_save)


# =========================
# evaluate
# =========================


def metric_permission_based_outlier(scores, marks, target_labels, title=None):
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    for i in range(len(target_labels)):
        label_i = target_labels[i]

        scores_i, y_true = [], []
        for j in range(len(scores)):
            if marks[j][i] != 0:
                scores_i.append(scores[j][i])
                y_true.append(1 if marks[j][i] == 1 else 0)

        pk, rk = [], []
        for k in range(1, len(y_true)):
            y_predict = get_label_n(y_true, scores_i, k)
            pk.append(precision_score(y_true, y_predict))
            rk.append(recall_score(y_true, y_predict))

        n = sum(y_true) - 1
        if 0 <= n < len(pk):
            # print(y_true)j
            # print(scores_i)
            print('{}@{}/{}'.format(label_i, n, len(scores_i)), pk[n], rk[n], roc_auc_score(y_true, scores_i))
        else:
            print('{}@{}/{}'.format(label_i, n, len(scores_i)), 0.0, 0.0, 0.0)

        if title is not None:
            fp_save = os.path.join('results_weighted', title)
            plot_curve('{}_{}_precision'.format(title, label_i), 'precision', list(range(1, len(y_true))), pk,
                       path_save=fp_save + '_{}_precision.pdf'.format(label_i))
            plot_curve('{}_{}_recall'.format(title, label_i), 'recall', list(range(1, len(y_true))), rk,
                       path_save=fp_save + '_{}_recall.pdf'.format(label_i))


def metric_overall_outlier(scores, weights, marks, title=None):
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    y_true = []
    weighted_scores = []
    for i in range(len(scores)):
        score = 0.0
        for w, s, m in zip(weights[i], scores[i], marks[i]):
            score += w * s

        # print(1 if 'n' in marks[i] else 0, score, scores[i], weights[i], marks[i])
        weighted_scores.append(score)
        y_true.append(1 if 1 in marks[i] else 0)

    pk, rk = [], []
    for k in range(1, len(y_true)):
        y_predict = get_label_n(y_true, weighted_scores, k)
        pk.append(precision_score(y_true, y_predict))
        rk.append(recall_score(y_true, y_predict))
    n = sum(y_true)
    print('overall@{}'.format(n), len(y_true), pk[n], rk[n], roc_auc_score(y_true, weighted_scores))

    if title is not None:
        fp_save = os.path.join('results', 'overall_' + title)
        # plot_curve('overall_{}_precision'.format(title), 'precision', list(range(1, len(y_true))), pk,
        #            fp_save=fp_save + '_precision.pdf')
        # plot_curve('overall_{}_recall'.format(title), 'recall', list(range(1, len(y_true))), rk,
        #            fp_save=fp_save + '_recall.pdf')
        plot_precision_recall(
            '', list(range(1, len(y_true))), pk, rk, path_save=fp_save + '.pdf'
        )
