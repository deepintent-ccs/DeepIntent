# -*- coding: UTF-8 -*-

import keras
import keras.layers as kl

from layers import DenseNet, CoAttentionParallel


def create_model_with_conf(vocab_size, label_size, conf):
    # inputs
    input_img = kl.Input(shape=conf.img_shape)
    input_text = kl.Input(shape=(conf.text_length,))

    # textual feature
    feature_dim = conf.feature_dim_half * 2
    embeddings = kl.Embedding(input_dim=vocab_size, output_dim=conf.text_embedding_dim,
                              mask_zero=True, input_length=conf.text_length)(input_text)
    feature_text = kl.Bidirectional(kl.GRU(conf.feature_dim_half, return_sequences=True))(embeddings)

    # image feature
    feature_img = DenseNet(input_img, conf.img_init_channels, conf.img_init_kernel, conf.img_init_pooling,
                           conf.img_num_blocks, conf.img_num_layers, conf.img_growth_rate,
                           conf.img_use_bottleneck, conf.img_compression, conf.img_dropout)
    img_target_shape = compute_image_target_shape(conf)
    feature_img = kl.Reshape(target_shape=img_target_shape)(feature_img)
    feature_img = kl.Dense(feature_dim, activation='tanh', use_bias=False)(feature_img)

    # feature combination
    feature_combine = CoAttentionParallel(dim_k=conf.att_dim, name='feature')([feature_text, feature_img])

    # output layer
    feature = kl.Dropout(rate=conf.feature_dropout)(feature_combine)
    y_ = kl.Dense(label_size, activation='sigmoid')(feature)

    # model
    model = keras.models.Model(inputs=[input_img, input_text], outputs=y_)
    return model


def compute_image_target_shape(conf):
    width, height, num_channels = conf.img_shape
    num_channels = conf.img_init_channels

    # compute pooling times
    num_pooling = conf.img_num_blocks - 1
    if conf.img_init_pooling:
        num_pooling += 1

    # each pooling reduce half of the width or height
    for _ in range(num_pooling):
        width = int(width / 2)
        height = int(height / 2)

    # channels increase num_layers * growth_rate for each dense blocks
    for _ in range(conf.img_num_blocks - 1):
        num_channels += conf.img_num_layers * conf.img_growth_rate
        # and compressed with compression rate
        num_channels = int(num_channels * conf.img_compression)

    # the last dense block is not compressed
    num_channels += conf.img_num_layers * conf.img_growth_rate

    return width * height, num_channels
