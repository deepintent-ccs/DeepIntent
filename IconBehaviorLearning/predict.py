# -*- coding: UTF-8 -*-

"""Use the pre-trained model to predict new data.
"""

import keras

from layers import CoAttentionParallel
from conf import PredictConf


def predict(predict_conf):
    predict_conf = PredictConf()

    # load data
    # 1. pre-processed data
    # 2. raw data from the extraction step

    # load model
    model = keras.models.load_model(predict_conf.path_model, custom_objects={
        "CoAttentionParallel": CoAttentionParallel
    })

    # predict with trained model
    model.load_weights(path_cp)
    y_predict = model.predict(x_test)
    y_true = y_test.tolist()

    # evaluate
    # 1. print metric results
    # 2. log predictions

    # save prediction
    if train_conf.is_log_prediction:
        path_predict = os.path.join(
            train_conf.path_output, 'model_{}_{}.predictions'.format(train_conf.code_name, i)
        )
        save_pkl_data(path_predict, [y_predict, y_test])

