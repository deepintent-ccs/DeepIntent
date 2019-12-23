# -*- coding: UTF-8 -*-

import os
from collections import namedtuple


PrepareConf = namedtuple('PrepareConf', [
    # path
    'path_data_in',         # String,
    'path_data_out',        # String,
    # settings
    'image_size',           # Tuple of int (i.e., width and height), images will be resized to feed the model
    'text_min_support',     # Int, text appeared less than the threshold will be removed
    'target_permissions',   #
    'target_groups',        #
])

ModelConf = namedtuple('ModelConf', [
    # path
    'path_model_save',
    'path_raw_data',        # extracted data before tokenize and resize image
    'path_data',            # prepared data to be feed into the model
    # image feature
    'img_size',             #
    'img_dropout',          # dropout rate in DenseNet
    # text feature
    'img_output_shape',     #
    # attention
    'att_type',             # attention type
    'path_save',            # path of trained model
    #
    'random_seed',          #
    'train_ratio',          #
    'evaluate_times',       #
])


target_permissions = {
    'INTERNET', 'CHANGE_WIFI_STATE',
    'ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'ACCESS_MOCK_LOCATION',
    'RECORD_AUDIO',
    'SEND_SMS', 'READ_SMS', 'WRITE_SMS', 'RECEIVE_SMS',
    'CAMERA',
    'CALL_PHONE',
    'WRITE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE',
    'READ_CONTACTS', 'WRITE_CONTACTS', 'GET_ACCOUNTS', 'MANAGE_ACCOUNTS', 'AUTHENTICATE_ACCOUNTS',
}

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


example_prepare_conf = PrepareConf(
    # path
    path_data_in=os.path.join('..', 'data', 'text_example', 'data.pkl'),
    # path_data_in=os.path.join('..', 'data', 'total', 'raw_data.benign.pkl'),
    path_data_out=os.path.join('..', 'data', 'text_example', ''),
    # settings
    image_size=(128, 128),
    text_min_support=5,
    # sensitive permissions and permission groups
    target_permissions=target_permissions,
    target_groups=target_groups,
)
