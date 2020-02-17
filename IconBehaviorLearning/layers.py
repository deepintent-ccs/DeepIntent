# -*- coding: UTF-8 -*-

import keras.layers as kl
from keras import backend as K
from keras.engine.topology import Layer

REMOVE_FACTOR = 10000


def image_conv_layer(x, num_channels, dropout=0, kernel_size=3):
    out = kl.BatchNormalization()(x)
    out = kl.Activation('relu')(out)
    out = kl.Convolution2D(num_channels, (kernel_size, kernel_size),
                           padding='same', use_bias=False)(out)
    if dropout > 0:
        out = kl.Dropout(dropout)(out)
    return out


def image_bottleneck_layer(x, num_channels, dropout, kernel_size=3):
    out = image_conv_layer(x, num_channels * 4, dropout, kernel_size=1)
    out = image_conv_layer(out, num_channels, dropout, kernel_size=kernel_size)
    return out


def image_dense_block(x, num_layers, growth_rate, dropout, bottleneck):
    for i in range(num_layers):
        # get layer output
        if bottleneck:
            out = image_bottleneck_layer(x, growth_rate, dropout)
        else:
            out = image_conv_layer(x, growth_rate, dropout)
        # merge them on the channel axis
        # concatenate input with layer output
        x = kl.Concatenate(axis=-1)([x, out])

    return x


def image_transition_block(x, num_channels, dropout):
    x = image_conv_layer(x, num_channels, dropout, kernel_size=1)
    x = kl.AveragePooling2D()(x)
    return x


def DenseNet(inputs, init_channels=8, init_kernel_size=(7, 7), init_pooling=True,
             num_blocks=3, num_layers=12, growth_rate=12,
             bottleneck=False, compression=1.0, dropout=0.0):
    x = inputs

    # initial convolution
    num_channels = init_channels
    out = kl.Conv2D(init_channels, init_kernel_size, padding='same', use_bias=False)(x)
    if init_pooling:
        out = kl.MaxPooling2D()(out)

    # dense block
    for i in range(num_blocks - 1):
        out = image_dense_block(out, num_layers, growth_rate, dropout, bottleneck)

        num_channels += num_layers * growth_rate
        num_channels = int(compression * num_channels)

        out = image_transition_block(out, num_channels, dropout)

    # last dense block
    out = image_dense_block(out, num_layers, growth_rate, dropout, bottleneck)

    return out


class CoAttentionParallel(Layer):
    """Self-defined parallel co-attention layer.

    inputs: [tFeature, iFeature]
    outputs: [coFeature]

    dimension:
    input dimensions: [(batch_size, seq_length, embedding_size), (batch_size, num_img_region, 2*hidden_size)]
        considering subsequent operation, better to set embedding_size == 2*hidden_size
    output dimensions:[(batch_size, 2*hidden_size)]
    """
    def __init__(self, dim_k, **kwargs):
        super(CoAttentionParallel, self).__init__(**kwargs)
        self.dim_k = dim_k  # internal tensor dimension
        self.supports_masking = True

    def build(self, input_shape):
        if not isinstance(input_shape, list):
            raise ValueError('A Co-Attention_para layer should be called '
                             'on a list of inputs.')
        if len(input_shape) != 2:
            raise ValueError('A Co-Attention_para layer should be called on a list of 2 inputs.'
                             'Got '+str(len(input_shape))+'inputs.')
        self.embedding_size = input_shape[0][-1]
        self.num_region = input_shape[1][1]
        self.seq_len = input_shape[0][1]

        # naming variables following the VQA paper
        self.Wb = self.add_weight(name='Wb',
                                  initializer='random_normal',
                                  # initializer='ones',
                                  shape=(self.embedding_size, self.embedding_size),
                                  trainable=True)
        self.Wq = self.add_weight(name='Wq',
                                  initializer='random_normal',
                                  # initializer='ones',
                                  shape=(self.embedding_size, self.dim_k),
                                  trainable=True)
        self.Wv = self.add_weight(name='Wv',
                                  initializer='random_normal',
                                  # initializer='ones',
                                  shape=(self.embedding_size, self.dim_k),
                                  trainable=True)
        self.Whv = self.add_weight(name='Whv',
                                   initializer='random_normal',
                                   # initializer='ones',
                                   shape=(self.dim_k, 1),
                                   trainable=True)
        self.bhv = self.add_weight(name='bhv',
                                   shape=(1,),
                                   initializer='zeros',
                                   trainable=True)
        self.Whq = self.add_weight(name='Whq',
                                   initializer='random_normal',
                                   # initializer='ones',
                                   shape=(self.dim_k, 1),
                                   trainable=True)
        self.bhq = self.add_weight(name='bhq',
                                   shape=(1,),
                                   initializer='zeros',
                                   trainable=True)

        super(CoAttentionParallel, self).build(input_shape)  # Be sure to call this somewhere!

    def call(self, inputs, mask=None):
        t_mask = mask[0]
        tFeature = inputs[0]
        iFeature = inputs[1]
        # affinity matrix C
        affi_mat = K.dot(tFeature, self.Wb)
        affi_mat = K.batch_dot(affi_mat, K.permute_dimensions(iFeature, (0, 2, 1)))  # (batch_size, seq_len, num_region)
        # Hq, Hv, av, aq
        # image
        tmp_Hv = K.dot(tFeature, self.Wq)
        Hv = K.dot(iFeature, self.Wv) + K.batch_dot(K.permute_dimensions(affi_mat, (0, 2, 1)), tmp_Hv)
        Hv = K.tanh(Hv)
        # image attention
        av = K.squeeze(K.dot(Hv, self.Whv) + K.expand_dims(self.bhv, 0), axis=-1)
        av = K.softmax(av)

        # text
        tmp_Hq = K.dot(iFeature, self.Wv)
        Hq = K.dot(tFeature, self.Wq) + K.batch_dot(affi_mat, tmp_Hq)
        Hq = K.tanh(Hq)
        # masked text attention
        aq = K.squeeze(K.dot(Hq, self.Whq) + K.expand_dims(self.bhq, 0), axis=-1)
        m = K.cast(t_mask, dtype='float32')
        m = m - 1
        m = m * REMOVE_FACTOR
        aq = aq + m
        aq = K.softmax(aq)

        av = K.permute_dimensions(K.repeat(av, self.embedding_size), (0, 2, 1))
        aq = K.permute_dimensions(K.repeat(aq, self.embedding_size), (0, 2, 1))

        tfeature = K.sum(aq * tFeature, axis=1)
        ifeature = K.sum(av * iFeature, axis=1)

        return tfeature + ifeature

    def get_config(self):
        config = {'dim_k': self.dim_k}
        base_config = super(CoAttentionParallel, self).get_config()

        return dict((k, v) for cfg in (config, base_config) for k, v in cfg.items())

    def compute_mask(self, inputs, mask=None):
        return None

    def compute_output_shape(self, input_shape):
        output_shape = (input_shape[0][0], input_shape[0][-1])
        return output_shape
