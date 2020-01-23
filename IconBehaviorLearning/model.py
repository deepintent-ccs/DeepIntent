# -*- coding: UTF-8 -*-

import keras
import keras.layers as kl

from layers import DenseNet, CoAttentionParallel


def create_model_with_conf(vocab_size, label_size, conf):
    # inputs
    input_img = kl.Input(shape=conf.img_shape)
    input_text = kl.Input(shape=(conf.text_length,))

    # textual feature
    gru_dim = conf.feature_dim / 2  # output_dim = 2 * gru_dim, due to bidirectional
    embeddings = kl.Embedding(input_dim=vocab_size, output_dim=conf.text_embedding_dim,
                              mask_zero=True, input_length=conf.text_length)(input_text)
    feature_text = kl.Bidirectional(kl.GRU(gru_dim, return_sequences=True))(embeddings)

    # image feature
    feature_img = DenseNet(input_img, conf.img_init_channels, conf.img_num_blocks,
                           conf.img_num_layers, conf.img_growth_rate,
                           conf.img_use_bottleneck, conf.img_compression)
    feature_img = kl.Reshape(target_shape=conf.img_target_shape)(feature_img)  # (8 * 8, 68)
    feature_img = kl.Dense(conf.feature_dim, activation='tanh', use_bias=False)(feature_img)

    # feature combination
    feature_combine = CoAttentionParallel(dim_k=conf.att_dim, name='feature')([feature_text, feature_img])

    # output layer
    feature = kl.Dropout(rate=conf.feature_dropout)(feature_combine)
    y_ = kl.Dense(label_size, activation='sigmoid')(feature)

    # model
    model = keras.models.Model(inputs=[input_img, input_text], outputs=y_)
    return model
