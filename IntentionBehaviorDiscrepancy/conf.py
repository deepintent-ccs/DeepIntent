# -*- coding: UTF-8 -*-

from collections import namedtuple


PreProcessConf = namedtuple('PreProcessConf', [
    # path
    'path_data_in',         # String, path of the raw data (after contextual text extraction)
    'path_data_out',        # String, path to save the pre-process data
    # image
    'image_min_size',       # Int, image's width and height should be higher than this value
    'image_wh_ratio',       # Int or float, the ratio between width and height should not be higher than this value
    # text
    'text_min_support',     # Int, text appeared less than the threshold will be removed
    # permissions
    'target_groups',        # Dict, map permissions to groups, the key of the dict is group name
])

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

TrainConf = namedtuple('TrainConf', [
    # train (and test) the model
    'code_name',            # String, the model name (for human understanding)
    'repeat_times',         # Int, how many times to train the model (for cross validation)
    'random_seed',          # Int, the random seed, to fix the data order
    # path
    'path_data',            # String, path of the training data
    'path_output',          # String, folder to contain the outputs
    # data partition
    'train_ratio',          # Float, training ratio to split data
    'valid_ratio',          # Float, validation ratio to split data
    'test_ratio',           # Float, testing ratio to split data
    'is_data_refresh',      # Boolean, whether to refresh data after each training process
    # callbacks
    'early_stop_patients',  # Int, Keras early stop parameter
    # fit parameters
    'batch_size',           # Int, the size of each batch
    'epochs',               # Int, the number of epochs
    'verbose',              # Int, verbose in Keras, 0 to 2, silent to verbose
    'monitor_type',         # String, could be 'val_loss' or 'val_acc'
    # log option
    'is_log_history',       # Boolean, whether to save the training history
    'is_log_prediction',    # Boolean, whether to save the predictions
    'is_log_avg_score',     # Boolean, whether to save the average scores
])

PredictConf = namedtuple('PredictConf', [
    # use the pre-trained model to predict the results
    # path
    'path_data',            # String, path of data
    'path_meta',            # String, path of meta data of the pre-trained model
    'path_model',           # String, path of the pre-trained model
    'path_predictions',     # String, path to save the predictions
    # prediction
    'threshold',            # Float, the value to , commonly could be set to 0.5
])


def check_conf(value, domain, fallback):
    """Keep the conf value within the specific domain.
    """
    return value if value in domain else fallback


target_groups = {
    'NETWORK': {'INTERNET', 'CHANGE_WIFI_STATE'},
    'LOCATION': {'ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'ACCESS_MOCK_LOCATION'},
    'MICROPHONE': {'RECORD_AUDIO'},
    'SMS': {'SEND_SMS', 'READ_SMS', 'WRITE_SMS', 'RECEIVE_SMS'},
    'CAMERA': {'CAMERA'},
    'CALL': {'CALL_PHONE'},
    'STORAGE': {'WRITE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE'},
    'CONTACTS': {'READ_CONTACTS', 'WRITE_CONTACTS',
                 'GET_ACCOUNTS', 'MANAGE_ACCOUNTS', 'AUTHENTICATE_ACCOUNTS'},
}
