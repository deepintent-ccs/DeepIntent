# -*- coding: UTF-8 -*-

from collections import namedtuple


ModelConf = namedtuple('ModelConf', [
    # image feature
    'img_shape',            # Tuple of int, (width, height, channels) of the image
    'img_re_sample',        # PIL re_sample type, e.g., PIL.Image.BILINEAR, PIL.Image.NEAREST
    'img_init_channels',    # Int, DenseNet initialize channels
    'img_init_kernel',      # Tuple of int, DenseNet initialize kernel shape
    'img_init_pooling',     # Boolean, whether to use pooling after the initialize convolution
    'img_num_blocks',       # Int, number of dense blocks, including the last dense block
    'img_num_layers',       # Int, number of layers in each dense block
    'img_growth_rate',      # Int, growth rate of the DenseNet
    'img_use_bottleneck',   # Boolean, whether to use the bottleneck layer
    'img_compression',      # Float, compression rate of DenseNet, below 1.0
    'img_dropout',          # Float, dropout rate of DenseNet, 0.0 means no dropout
    # text feature
    'text_length',          # Int, length of the texts
    'text_embedding_dim',   # Int, embedding dim of each word
    # feature
    'feature_dim_half',     # Int, half size of the combined feature, also used as GRU dim
    'feature_dropout',      # Float, dropout rate of combined feature
    # attention
    'att_dim',              # Int, inner attention dim K
])
