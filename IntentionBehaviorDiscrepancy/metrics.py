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


def metric_permission_based_outlier(scores, marks, target_permissions, title=None):
    """Metric and print permission based outlier scores, i.e., precision/recall and AUC value.

    :param scores:
        List, scores(i, j) of each widget(i) in each permission(j).
    :param marks:
        List, outlier marks(i, j) of each widget(i) in each permission(j).
        The value could be 0 (not related to the permission), 1 (outlier), -1 (inlier).
    :param target_permissions:
        List of string, the `j`th permission name.
    :param title:
        String, file name used to save the plot, `None` means not to save.

    :return: None
    """
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    for i in range(len(target_permissions)):
        permission_i = target_permissions[i]

        # sort scores in each permission
        scores_i, y_true = [], []
        for j in range(len(scores)):
            if marks[j][i] != 0:
                scores_i.append(scores[j][i])
                y_true.append(1 if marks[j][i] == 1 else 0)

        # no positive or negative labels
        if sum(y_true) == len(scores_i) or sum(y_true) == 0:
            print('{}({}/{}), error'.format(
                permission_i, sum(y_true), len(scores_i)
            ))
            continue

        # compute precision, recall curve and auc value
        pk, rk = [], []
        for k in range(1, len(y_true)):
            y_predict = get_label_n(y_true, scores_i, k)
            pk.append(precision_score(y_true, y_predict))
            rk.append(recall_score(y_true, y_predict))
        auc = roc_auc_score(y_true, scores_i)

        # print top-k precision, recall, and AUC value
        k = sum(y_true)
        print('{}({}/{}), p/r: {}, AUC: {}'.format(
            permission_i, k, len(scores_i), round(pk[k - 1], 4), round(auc, 4)
        ))

        # save plot
        if title is not None:
            path_save = os.path.join('{}-{}.pdf'.format(title, permission_i))
            plot_precision_recall(
                permission_i, list(range(1, len(y_true))), pk, rk, path_save
            )


def metric_overall_outlier(scores, marks, title=None):
    """Metric global outlier results, i.e., precision/recall and AUC value.

    :param scores:
        List, summed scores of each widget(i).
    :param marks:
        List, outlier marks(i, j) of each widget(i) in each permission(j).
        The value could be 0 (not related to the permission), 1 (outlier), -1 (inlier).
        If there is one outlier in the related permission, then the widget is outlier.
    :param title:
        String, file name used to save the plot, `None` means not to save.

    :return: None
    """
    from pyod.utils.utility import get_label_n
    from sklearn.metrics.ranking import roc_auc_score
    from sklearn.metrics.classification import precision_score, recall_score

    # get global outlier mark
    y_true = [1 if 1 in marks[i] else 0 for i in range(len(scores))]

    # compute precision, recall curve and auc value
    pk, rk = [], []
    for k in range(1, len(y_true)):
        y_predict = get_label_n(y_true, scores, k)
        pk.append(precision_score(y_true, y_predict))
        rk.append(recall_score(y_true, y_predict))
    auc = roc_auc_score(y_true, scores)

    # print top-k precision, recall, and AUC value
    k = sum(y_true)
    print('overall({}/{}), p/r: {}, AUC: {}'.format(
        k, len(y_true), round(pk[k - 1], 4), round(auc, 4)
    ))

    # save plot
    if title is not None:
        path_save = os.path.join('{}.pdf'.format(title))
        plot_precision_recall(
            'Overall', list(range(1, len(y_true))), pk, rk, path_save
        )
