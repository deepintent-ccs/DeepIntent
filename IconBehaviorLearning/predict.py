# -*- coding: UTF-8 -*-

"""Use the pre-trained model to predict new data.
"""

import keras

from conf import PredictConf
from tools import save_pkl_data, load_pkl_data
from layers import CoAttentionParallel
from prepare import prepare_data
from metrics import evaluate


def predict(predict_conf):
    # load data
    data = load_pkl_data(predict_conf.path_data)

    # load model meta data
    meta = load_pkl_data(predict_conf.path_meta)
    meta_image_shape = meta['TrainConf'].img_shape
    meta_re_sample_type = meta['TrainConf'].img_re_sample
    meta_text_len = meta['TrainConf'].text_length
    meta_label_num = len(meta['label2id'])

    # load model
    model = keras.models.load_model(predict_conf.path_model, custom_objects={
        "CoAttentionParallel": CoAttentionParallel
    })

    # prepare data
    _, _, data_test = prepare_data(data, meta_image_shape, meta_re_sample_type,
                                   meta_text_len, meta_label_num, 0, 0)

    # predict with trained model
    x_test, y_test = data_test
    y_predict = model.predict(x_test)
    y_true = y_test.tolist()

    # save predictions
    save_pkl_data(predict_conf.path_predictions, [y_predict, y_test])

    # print metric results
    evaluate(y_true, y_predict, predict_conf.predict_threshold)


def total_example():
    import os

    path_current = os.path.dirname(os.path.abspath(__file__))
    path_data = os.path.join(path_current, '..', 'data')
    predict_conf = PredictConf(
        # path
        path_data=os.path.join(path_data, 'total', 'data.mal.pkl'),
        path_meta=os.path.join(path_data, 'total', 'deepintent.meta'),
        path_model=os.path.join(path_data, 'total', 'deepintent.model'),
        path_predictions=os.path.join(path_data, 'total', 'mal.predictions'),
        # prediction
        threshold=0.5
    )

    predict(predict_conf)


def main():
    total_example()


if __name__ == '__main__':
    main()
