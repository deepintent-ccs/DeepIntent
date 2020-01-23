# -*- coding: UTF-8 -*-

"""Load the data and train the model.
Could also enable the validate and test phase.
"""

import os
import codecs
import random

from keras.callbacks import EarlyStopping, ModelCheckpoint

import prepare
import metrics
from conf import ModelConf, TrainConf
from model import create_model_with_conf
from tools import load_pkl_data, save_pkl_data


def split_data_with_conf(data, label_size, train_conf, model_conf):
    train_ratio, valid_ratio, test_ratio = normalize_data_ratio(
        train_conf.train_ratio, train_conf.valid_ratio, train_conf.test_ratio
    )
    data_train, data_valid, data_test = prepare.prepare_data(
        data, model_conf.img_shape, model_conf.img_re_sample,
        model_conf.text_length, label_size, train_ratio, valid_ratio
    )

    return data_train, data_valid, data_test


def normalize_data_ratio(r1, r2, r3):
    rs = r1 + r2 + r3
    return r1 / rs, r2 / rs, r3 / rs


def save_on_condition(condition, path_out, content):
    if condition:
        save_pkl_data(path_out, content)


def train(model_conf, train_conf):
    model_conf = ModelConf()
    train_conf = TrainConf()

    # set up random seed
    random.seed(train_conf.random_seed)

    # check the output path
    if not os.path.exists(train_conf.path_output):
        os.makedirs(train_conf.path_output)

    # load and statistics
    (vocab2id, label2id), data = load_pkl_data(train_conf.path_data)
    id2vocab = {v: k for k, v in vocab2id.items()}
    id2label = {v: k for k, v in label2id.items()}
    token_size, label_size = len(id2vocab), len(id2label)
    label_names = [id2label[i] for i in range(len(id2label))]
    print('label size:', label_size, 'token size:', token_size)
    print('label names:', label_names)

    # split data
    train_ratio, valid_ratio, test_ratio = normalize_data_ratio(
        train_conf.train_ratio, train_conf.valid_ratio, train_conf.test_ratio
    )
    data_train, data_valid, data_test = prepare.prepare_data(
        data, model_conf.img_shape, model_conf.img_re_sample,
        model_conf.text_length, label_size, train_ratio, valid_ratio
    )
    (x_train, y_train), (x_valid, y_valid), (x_test, y_test) = data_train, data_valid, data_test
    print('train: {0}; valid: {1}; test: {2}'.format(len(y_train), len(y_valid), len(y_test)))

    # train and test
    scores = []
    predict_threshold = 0.5
    for i in range(train_conf.repeat_times):
        print('{sp}\ntime {i}\n{sp}'.format(sp='=' * 20, i=i))
        # prefix to save the training process
        path_prefix = os.path.join(
            train_conf.path_output, 'model_{}_{}'.format(train_conf.code_name, i)
        )

        # create and train the model
        model = create_model_with_conf(token_size, label_size, model_conf)
        model.compile(loss='binary_crossentropy', optimizer='adam', metrics=['accuracy'])

        # init callbacks
        path_cp = path_prefix + '.cp'
        es = EarlyStopping(monitor=train_conf.monitor_type, patience=train_conf.early_stop_patients)
        cp = ModelCheckpoint(filepath=path_cp, monitor=train_conf.monitor_type, save_best_only=True)

        # fit the model
        history = model.fit(x_train, y_train,
                            batch_size=train_conf.batch_size, epochs=train_conf.epochs,
                            verbose=train_conf.verbose, validation_data=(x_valid, y_valid),
                            callbacks=[cp, es])

        # save training history
        save_on_condition(train_conf.is_log_history, path_prefix + '.his', history.history)
        # save the trained model
        model.save(path_prefix + '.h5')

        # test if test_ratio > 0
        if test_ratio > 0:
            # predict with trained model
            model.load_weights(path_cp)
            y_predict = model.predict(x_test)
            y_true = y_test.tolist()

            # save prediction
            if train_conf.is_log_prediction:
                path_predict = path_prefix + '.predictions'
                save_pkl_data(path_predict, [y_predict, y_test])

            # evaluate
            scores = metrics.evaluate(y_true, y_predict, predict_threshold)
            metrics.display_scores(scores, label_names)
            scores.append(scores)

        # prepare for next loop
        if train_conf.is_data_refresh:
            data_train, data_valid, data_test = prepare.prepare_data(
                data, model_conf.img_shape, model_conf.img_re_sample,
                model_conf.text_length, label_size, train_ratio, valid_ratio
            )
            (x_train, y_train), (x_valid, y_valid), (x_test, y_test) = data_train, data_valid, data_test

    if test_ratio > 0:
        # average score
        avg_scores = metrics.compute_mean_var(scores)
        metrics.display_average_scores(avg_scores, label_names, train_conf.repeat_times)

        # store average score
        if train_conf.is_log_avg_score:
            path_avg = os.path.join(
                train_conf.path_output, 'result_{}.avg.txt'.format(train_conf.code_name)
            )
            with codecs.open(path_avg, mode='w', encoding='UTF-8') as fo:
                metrics.display_average_scores(avg_scores, label_names, train_conf.repeat_times,
                                               is_k_print=True, fo=fo)


def main():
    pass


if __name__ == '__main__':
    main()
