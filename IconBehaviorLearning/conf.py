# -*- coding: UTF-8 -*-

import os
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
    'img_shape',            #
    'img_re_sample',        #
    'img_init_channels',
    'img_num_blocks',
    'img_num_layers',
    'img_growth_rate',
    'img_use_bottleneck',
    'img_compression',
    'img_target_shape',
    # text feature
    'text_length',          #
    'text_embedding_dim',
    # feature
    'feature_dim',
    'feature_dropout',      # Float,
    # attention
    'att_dim',              # Int, inner attention dim K
])

TrainConf = namedtuple('TrainConf', [
    # train (and test) the model
    'code_name',
    'repeat_times',
    'random_seed',
    # path
    'path_data',
    'path_output',
    # data partition
    'train_ratio',
    'valid_ratio',
    'test_ratio',
    'is_data_refresh',
    # callbacks
    'early_stop_patients',
    # fit parameters
    'batch_size',
    'epochs',
    'verbose',
    'monitor_type',         # String, could be 'val_loss' or 'val_acc'
    # log option
    'is_log_history',
    'is_log_prediction',
    'is_log_avg_score',     # Boolean, whether to save the average scores
])

PredictConf = namedtuple('PredictConf', [
    # use the pre-trained model to predict the results
    # path
    'path_data',
    'path_model',
    'path_predictions',
    #
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


_current_abs_path = os.path.dirname(os.path.abspath(__file__))
_data_abs_path = os.path.join(_current_abs_path, '..', 'data')
# example_prepare_conf = PreProcessConf(
#     # path
#     path_data_in=os.path.join('..', 'data', 'text_example', 'data.pkl'),
#     # path_data_in=os.path.join('..', 'data', 'total', 'raw_data.benign.pkl'),
#     path_data_out=os.path.join('..', 'data', 'text_example', ''),
#     # settings
#     image_size=(128, 128),
#     text_min_support=5,
#     # sensitive permissions and permission groups
#     target_permissions=target_permissions,
#     target_groups=target_groups,
# )
