# -*- coding: UTF-8 -*-

from keras import backend as K
from keras.engine.topology import Layer

REMOVE_FACTOR = 10000


class CoAttentionParallel(Layer):
    """
    self-defined parallel co-attention layer.
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
        """
        naming variables following the VQA paper
        """
        self.Wb = self.add_weight(name="Wb",
                                  initializer="random_normal",
                                  # initializer="ones",
                                  shape=(self.embedding_size, self.embedding_size),
                                  trainable=True)
        self.Wq = self.add_weight(name="Wq",
                                  initializer="random_normal",
                                  # initializer="ones",
                                  shape=(self.embedding_size, self.dim_k),
                                  trainable=True)
        self.Wv = self.add_weight(name="Wv",
                                  initializer="random_normal",
                                  # initializer="ones",
                                  shape=(self.embedding_size, self.dim_k),
                                  trainable=True)
        self.Whv = self.add_weight(name="Whv",
                                   initializer="random_normal",
                                   # initializer="ones",
                                   shape=(self.dim_k, 1),
                                   trainable=True)
        self.bhv = self.add_weight(name="bhv",
                                   shape=(1,),
                                   initializer="zeros",
                                   trainable=True)
        self.Whq = self.add_weight(name="Whq",
                                   initializer="random_normal",
                                   # initializer="ones",
                                   shape=(self.dim_k, 1),
                                   trainable=True)
        self.bhq = self.add_weight(name="bhq",
                                   shape=(1,),
                                   initializer="zeros",
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
        m = K.cast(t_mask, dtype="float32")
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
