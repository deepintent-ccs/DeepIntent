# -*- coding: UTF-8 -*-

import os
import csv
import codecs
import zipfile


def load_program_analysed_csv(path):
    """Load the program analysis results.

    The results could be saved in a .csv or a .zip file. If it is a .zip file,
    the prefix should be the same with the zipped .csv file. E.g., A.csv is
    stored in A.csv.zip or A.zip.

    :param path:
        String, path of the program analyse result, i.e., a .csv or .zip file.

    :return:
        title: List, the csv title;
        data: List, csv rows
    """
    base_name = os.path.basename(path)
    file_name, file_ext = os.path.splitext(base_name)

    if file_ext == '.zip':
        # get the zipped file name
        zipped_name = ''
        with zipfile.ZipFile(path, mode='r') as zf:
            if file_name in zf.namelist():
                zipped_name = file_name
            elif file_name + '.csv' in zf.namelist():
                zipped_name = file_name + '.csv'
            elif len(zf.namelist()) == 1:
                zipped_name = zf.namelist()[0]
        reader = csv.reader(zip_string_iter(path, zipped_name))
    else:
        reader = csv.reader(codecs.open(path))

    title = next(reader)
    data = [row[:-1] + [decode_permission(row[-1])] for row in reader]

    return title, data


def zip_string_iter(path, name):
    """Load zipped file line by line.

    :param path:
        String, the path of .zip file.
    :param name:
        String, the file to be read in the zipped file.

    :return:
        line: String, Textual contents line by line.
    """
    with zipfile.ZipFile(path, mode='r') as zf:
        with zf.open(name, mode='r') as zi:
            for line in zi:
                line = codecs.decode(line)
                yield line


def decode_permission(raw_perms):
    """Transform permissions into Python lists.

    The permission column from the previous output might be: [p1], "[p1, p2]",
    or "['p1', 'p2']". This function will decode these 3 types into python list
    object and remove duplicate permission.

    :param raw_perms:
        String, the permissions column saved in the csv file.
        E.g., [p1], "[p1, p2]", "['p1', 'p2']".

    :return:
        permissions: List, list of unique string (permission).
    """
    if raw_perms.find("'") > 0:  # for "['p1', 'p2']" format, simple eval it
        perms = eval(raw_perms)
    else:
        perms = [p.strip() for p in raw_perms.strip('[]').split(',')]

    return list(set(perms))


def flatten_data(raw_data):
    """Flatten the multi-level dict data.

    :param raw_data:
        Dict, with each level means: apk, icon, layout, permissions.

    :return:
        data: List, Flattened data.
    """
    data = []
    for app, app_vs in raw_data.items():
        for image, image_vs in app_vs.items():
            for layout, perms in image_vs.items():
                data.append([app, image, layout, perms])

    return data


def merge_icon_permissions(raw_data):
    """Associate icon and permissions from the loaded csv data.

    Notice that, icon might disappear in different layout and have different
    contextual texts. In this case, each <icon, layout> pair is treated as a
    single data.

    :param raw_data:
        List, the loaded csv data with the following columns, ['APK', 'Image',
        'WID', 'WID Name', 'Layout', 'Handler', 'Method', 'Permissions'].

    :return:
        data: List, list of <apk, icon_name, layout, permissions> tuple.
    """
    data = {}  # apk - image - layout - perms
    for raw in raw_data:
        # ['APK', 'Image', 'WID', 'WID Name', 'Layout', 'Handler', 'Method', 'Permissions']
        apk, image, _, _, layout, _, _, perms = raw

        if apk not in data:
            data[apk] = {}
        if image not in data[apk]:
            data[apk][image] = {}
        if layout not in data[apk][image]:
            data[apk][image][layout] = set()

        data[apk][image][layout].update(perms)

    return data


def load_data(path):
    """Load and format program analysis results.

    :param path:
        String, path of the program analysis results.

    :return:
        data: List, each row contains [app_name, img_name, layout, permissions].
    """
    title, raw_data = load_program_analysed_csv(path)
    icon_perms = merge_icon_permissions(raw_data)
    data = flatten_data(icon_perms)

    return data
