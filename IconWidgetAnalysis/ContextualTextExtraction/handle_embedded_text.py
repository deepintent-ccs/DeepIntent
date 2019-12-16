# -*- coding: UTF-8 -*-

import os
import codecs
import random

import cv2
import pytesseract
import numpy as np
from PIL import Image

from tools import image_compress, parse_xml_string

# =========================
# extract image
# =========================


def extract_drawable_image(app_name, img_name, path_decoded, is_log=False):
    """Extract drawable image from decoded App files.

    In order to extract embedded texts, we first need to get the image resource. This
    function will lookup app_name in the path_decoded and go through 'res' folder to find
    the drawable image.

    As images might be described by xml file, this method will also parse the xml file
    and looking for the image recursively.

    An img_name might also related to several real drawable images, e.g., different size
    of different button status. The method will try to find the largest image (might contain
    more details) and the normal status image (e.g., not clicked, as it might be first seen
    by users).

    If the target image is found, the method will return the compressed image (to reduce
    the memory use), the target image path and all the related lookup paths (to record the
    total recursively finding process).

    :param app_name:
        String, name of the target App.
    :param img_name:
        String, name of the target image.
    :param path_decoded:
        String, path that contains all the decoded Apps, especially the target App.
    :param is_log:
        Boolean, whether to print information when image is not find.

    :return:
        compressed_img: the compressed binaries with meta information (width, height and
        image mode). Can be encode, decode or display through methods in tools.py;
        img_path: the target image path;
        img_locations: related lookup paths.
    """
    img_locations = prepare_image_locations(path_decoded, app_name, img_name)
    sorted_locations = prepare_sorted_locations(img_locations)
    compressed_img, img_path = prepare_image_rgb(path_decoded, app_name, img_name, sorted_locations)

    if is_log and compressed_img is None:
        print('Can not find image:', app_name, img_name)
    return compressed_img, img_path, img_locations


def prepare_image_locations(path_decoded, app_name, image_name):
    """Find the image locations and record the finding processes.

    One image might have several paths (mostly due to different drawable dpi) and might
    also be described by a XML file which need to parse the XML to find the real image.

    This method will find the image path recursively in app's `res` folder and return the
    total finding records. Each record is a [path, type, label] triple. `Path` is the
    relative path in the App folder. `Type` could be `image` or `xml`. `Label` is used
    internal to decide the priority of the found image.

    :param path_decoded:
        String, the path of all the decoded Apps.
    :param app_name:
        String, the target App name.
    :param image_name:
        String, the target image name.

    :return:
        img_path: List, stored the finding records.
    """
    img_paths = []  # [[[path_1, type_1, label_1], ... ]]
    path_app = os.path.join(path_decoded, app_name)
    path_res = os.path.join(path_app, 'res')
    for res_name in os.listdir(path_res):
        if res_name.find('drawable') < 0:
            continue

        # for all drawable paths
        path_draw = os.path.join(path_res, res_name)
        for res_img_path in os.listdir(path_draw):
            res_img_name, res_img_ext = os.path.splitext(res_img_path)
            res_img_name = res_img_name.split('.')[0]
            # check the file name and the image name
            if res_img_name == image_name:
                path_match = os.path.join('res', res_name, res_img_path)
                if res_img_ext == '.xml':  # parse the xml file
                    xml_img_paths = find_xml_image_paths(path_decoded, app_name, path_match)
                    if xml_img_paths is not None:
                        img_paths.extend(xml_img_paths)
                else:  # find the image
                    img_paths.append([[path_match, 'image', 'direct']])

    return img_paths


def find_xml_image_paths(path_decoded, app_name, path_xml):
    """Parse XML file to find the image.

    This method will parse xml file to find the described image. It mainly considered the
    image button type (i.e., xml tag 'selector') and animation type (i.e., xml tag 'animation-
    list').

    Note that, the found image might still be described in the XML file. So, it is a recursive
    process.

    :param path_decoded:
        String, path of the decoded Apps.
    :param app_name:
        String, the name of the App.
    :param path_xml:
        String, relative XML path under the decoded app folder.

    :return:
        results: List, the XML parsing results.
    """
    path_xml_full = os.path.join(path_decoded, app_name, path_xml)
    xml_string = codecs.open(path_xml_full, encoding='UTF-8').read()
    root, ns = parse_xml_string(xml_string)

    if root.tag == 'animation-list':  # first item
        results = find_xml_image_animation(path_decoded, app_name, root, ns)
    elif root.tag == 'selector':  # default item with least attrib
        results = find_xml_image_button(path_decoded, app_name, root, ns)
    else:  # 'shape' or 'other' -> random select a drawable item
        results = find_xml_image_random(path_decoded, app_name, root, ns)

    if results is None or len(results) == 0:
        return None
    for trace in results:
        trace.insert(0, [path_xml, 'xml', root.tag])
    return results


def find_xml_image_animation(path_decoded, app_name, root, ns):
    """Get image from animation XML type.

    The animation images are usually similar (different pose of the same object). Thus,
    the method simply return the first frame image.

    :param path_decoded:
        String, path of the decoded Apps.
    :param app_name:
        String, the name of the App.
    :param root:
        XML tree object, the root of the parsed XML.
    :param ns:
        Dict, XML namespace.

    :return:
        results: List, the XML parsing results.
    """
    results = []
    target_attrib = '{{{}}}drawable'.format(ns['android'])
    for node in root.iter():
        # found a drawable image
        if target_attrib in node.attrib:
            img_desc = node.attrib[target_attrib]
            img_name = img_desc.split('/')[-1]
            img_paths = prepare_image_locations(path_decoded, app_name, img_name)

            # record the index
            if len(img_paths) > 0:
                t = 'first' if len(results) == 0 else 'later'
                for img_path in img_paths:
                    img_path[-1][-1] = t
                results.extend(img_paths)

    return results


def find_xml_image_button(path_decoded, app_name, root, ns):
    """Get image from image button XML type.

    We try to find the original look in image button type, since users always see them at
    the first time without any interaction. In detail, the original look usually contain
    least attributes. So, we treat the image that contain least attributes as the target
    image.

    :param path_decoded:
        String, path of the decoded Apps.
    :param app_name:
        String, the name of the App.
    :param root:
        XML tree object, the root of the parsed XML.
    :param ns:
        Dict, XML namespace.

    :return:
        results: List, the XML parsing results.
    """
    results = []
    target_index = -1
    min_attrib_len = 100

    target_attrib = '{{{}}}drawable'.format(ns['android'])
    for node in root.iter():
        if target_attrib not in node.attrib:
            continue

        # found a drawable image
        img_desc = node.attrib[target_attrib]
        img_name = img_desc.split('/')[-1]
        img_paths = prepare_image_locations(path_decoded, app_name, img_name)

        # found the real image
        if img_paths is not None:
            # update the image that has minimal attribute number
            if len(node.attrib) < min_attrib_len:
                target_index = len(results)
                min_attrib_len = len(node.attrib)
            for img_path in img_paths:
                img_path[-1][-1] = 'btn_{}'.format(len(node.attrib))
            results.append(img_paths)

    # mark the image that has minimal attribute number as the target
    if target_index >= 0:
        for img_path in results[target_index]:
            img_path[-1][-1] += '_target'

    # format results
    results_new = []
    for result in results:
        results_new.extend(result)
    return results_new


def find_xml_image_random(path_decoded, app_name, root, ns):
    """Unknown XML type, find an image randomly.

    :param path_decoded:
        String, path of the decoded Apps.
    :param app_name:
        String, the name of the App.
    :param root:
        XML tree object, the root of the parsed XML.
    :param ns:
        Dict, XML namespace.

    :return:
        results: List, the XML parsing results.
    """
    results = []
    target_attrib = '{{{}}}drawable'.format(ns['android'])
    # find all the drawable images
    for node in root.iter():
        try:
            img_desc = node.attrib[target_attrib]
            img_name = img_desc.split('/')[-1]
            img_paths = prepare_image_locations(path_decoded, app_name, img_name)
            if img_paths is not None:
                results.append(img_paths)
        except KeyError:
            continue

    # randomly select an image
    results_new = []
    target_index = random.randint(0, len(results) - 1) if len(results) > 0 else -1
    for i in range(len(results)):
        label = 'chosen' if i == target_index else 'candidate'
        for img_path in results[i]:
            img_path[-1][-1] = label
            results_new.append(img_path)

    return results_new


def prepare_sorted_locations(image_locations):
    """Sort the image locations.

    The order is defined as follows:
    direct > image button (target > big > small) >
    animation (first > later) > random (chosen > candidate)

    :param image_locations:
        List, the found image locations.

    :return:
        results: List, sorted image locations.
    """
    def loc_key(location):
        location = location[-1][-1]
        if location == 'direct':
            return '0'
        elif location.startswith('btn'):
            if location.endswith('target'):
                return '10'
            else:
                return '1' + location.split('_')[1]
        elif location == 'first':
            return '2'
        elif location == 'second':
            return '3'
        elif location == 'chosen':
            return '4'
        elif location == 'candidate':
            return '5'
        else:
            return '6'

    sorted_loc = {}
    for loc in image_locations:
        lk = loc_key(loc)
        if lk not in sorted_loc:
            sorted_loc[lk] = []
        sorted_loc[lk].append(loc[-1][0])

    return [item[1] for item in sorted(sorted_loc.items(), key=lambda k: k[0])]


def prepare_image_rgb(fd_decoded, app_name, image_name, sorted_locations, is_log=False):
    """Get the most related and largest image from the sorted image locations.
    """
    # select largest image in the same sorting level
    fd_app = os.path.join(fd_decoded, app_name)
    for locations in sorted_locations:
        img, img_path = get_largest_image(fd_app, locations)
        if img is not None:
            if is_log:
                print('target image ({}-{}) found:'.format(app_name, image_name), img_path,
                      img.mode, '({}, {})'.format(img.width, img.height))
            return image_compress(img), img_path

    return None, ''


def get_image(path):
    """Load the image and return `None` if failed.
    """
    if not os.path.exists(path):
        return None
    else:
        try:
            return Image.open(path)
        except OSError:
            return None


def get_largest_image(path_app, image_locations):
    """Return the largest image.

    The method uses the area of the image (width * height) to sort
    the image size.
    """
    max_size = 0
    target_img = None
    target_path = None
    for loc in image_locations:
        fd_loc = os.path.join(path_app, loc)
        img = get_image(fd_loc)
        if img is None:
            continue

        img_size = img.width * img.height
        if img_size > max_size:
            target_img = img
            target_path = loc
            max_size = img_size

    if target_img is not None:
        if target_img.mode == 'P':
            target_img = target_img.convert()
    return target_img, target_path


# =========================
# extract embedded text
# =========================


def extract_embedded_text(app_name, img_name, compressed_img, east_model,
                          default_lang, fallback_lang,
                          ocr_size, ocr_padding, enable_cache):
    """Extract embedded texts based on the given image.

    This method first

    :param app_name:
        String, App's name, used for cache the embedded texts.
    :param img_name:
        String, image's name, used for cache the embedded texts.
    :param compressed_img:
        Tuple, the compressed image tuple, can be decoded to the original image.
    :param east_model:
        OpenCV model, the loaded pre-trained EAST model.
    :param default_lang:
        String, Tesseract OCR uses language
    :param fallback_lang:
        String,
    :param ocr_size:
        Tuple of int,
    :param ocr_padding:
        Float,
    :param enable_cache:
        Boolean,

    :return:
        texts: List of string or empty if failed.
    """
    # initialize
    if not hasattr(extract_embedded_text, 'cache'):
        extract_embedded_text.cache = {}  # key: app-img-(w, h); value: texts
    cache = extract_embedded_text.cache

    if compressed_img is not None:
        img_mode, (w, h), img_data = compressed_img
        if w / h > 10 or h / w > 10:
            pass
        else:
            key = '{}-{}-({}, {})'.format(app_name, img_name, w, h)
            if enable_cache and key in cache:
                return cache[key]

            image = Image.frombytes(img_mode, (w, h), img_data)
            texts = prepare_ocr_text(image, east_model, default_lang, fallback_lang, ocr_size, ocr_padding)
            cache[key] = texts
            return texts

    return []


def load_east_model(path_east):
    """Load the pre-trained East model using OpenCV.
    """
    return cv2.dnn.readNet(path_east)


def prepare_ocr_text(image, east, lang1, lang2, ocr_size, ocr_padding):
    """
      We follow PyImageSearch's tutorial, OpenCV OCR and text recognition with
    Tesseract, (https://www.pyimagesearch.com) to extract embedded texts.
    """
    # convert image to meet OpenCV format (BGR)
    image = image.convert('RGB')
    cv2_image = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)

    # detect text position
    #   We use EAST model (https://github.com/argman/EAST) to detect text
    # position.
    image_rest, (rw, rh) = ocr_resize_image(cv2_image, ocr_size)
    east_boxes = ocr_east(image_rest, east)
    boxes = ocr_resize_boxes(east_boxes, rw, rh)
    boxes = [(0, 0, image.width, image.height)] if len(boxes) == 0 else boxes

    # language name transfer
    #   First, we need to transfer language names
    # into short names which are used in Tesseract. For example, 'chi_sim'
    # is the short name of 'simplified Chinese'.
    #   Currently, we only consider English, simplified Chinese, Japanese,
    # Korean.
    lang_map = {
        'english': 'eng',
        'chinese': 'chi_sim',
        'japanese': 'jpn',
        'korean': 'kor',
    }
    target_lang = [lang_map[lang] if lang in lang_map.keys() else lang
                   for lang in [lang1, lang2]]

    # OCR
    #   We use Tesseract (https://github.com/tesseract-ocr/tesseract) to extract
    # embedded texts.
    #   We first try lang1 to extract embedded texts. If we can not find any result,
    # we will use lang2 as fallback. To use these languages, you should download
    # each pre-trained model in Tesseract to activate them.
    for lang in target_lang:
        ocr_results = ocr_tesseract(cv2_image, boxes, lang, ocr_padding)
        ocr_results = [r[-1] for r in ocr_results if len(r[-1]) > 0]
        if len(ocr_results) > 0:
            return ocr_results

    # return empty list
    #   If we can not find any embedded texts with the consideration of two
    # target languages.
    return []


def ocr_resize_image(image, ocr_size):
    """Resize the image and record the transform ratio.
    """
    # get original width and height
    #   Shape of OpenCV image is [height, width, channels].
    ori_height, ori_width = image.shape[:2]

    # get new width and height
    new_width, new_height = ocr_size

    # compute the resize ratio
    rw = ori_width / float(new_width)
    rh = ori_height / float(new_height)

    # resize the image
    #   When setting the size to cv2.resize() etc., it needs to
    # be (width, height).
    image = cv2.resize(image, (new_width, new_height))

    return image, (rw, rh)


def ocr_east(image, east):
    """Detect textual blocks.

    This method uses loaded EAST model to predict the textual rectangles.
    This method will also construct a blob from the image To improve the
    detection results.

    :param image:
    :param east:
    :return:
    """
    # define the two output layer names for the EAST detector model that
    # we are interested -- the first is the output probabilities and the
    # second can be used to derive the bounding box coordinates of text
    layer_names = [
        'feature_fusion/Conv_7/Sigmoid',
        'feature_fusion/concat_3'
    ]

    # construct a blob from the image and then perform a forward pass of
    # the model to obtain the two output layer sets
    h, w = image.shape[:2]
    blob = cv2.dnn.blobFromImage(image, 1.0, (h, w),
                                 (123.68, 116.78, 103.94), swapRB=True, crop=False)
    east.setInput(blob)
    (scores, geometry) = east.forward(layer_names)

    # decode the predictions, then apply non-maxima suppression to
    # suppress weak, overlapping bounding boxes
    (rects, confidences) = ocr_decode_predictions(scores, geometry)
    boxes = ocr_non_max_suppression(np.array(rects), probs=confidences)

    return boxes


def ocr_decode_predictions(scores, geometry, min_confidence=0.6):
    (numRows, numCols) = scores.shape[2:4]
    rectangles = []
    confidences = []

    # loop over the number of rows
    for y in range(0, numRows):
        # extract the scores (probabilities), followed by the
        # geometrical data used to derive potential bounding box
        # coordinates that surround text
        scores_data = scores[0, 0, y]
        x_data0 = geometry[0, 0, y]
        x_data1 = geometry[0, 1, y]
        x_data2 = geometry[0, 2, y]
        x_data3 = geometry[0, 3, y]
        angles_data = geometry[0, 4, y]

        # loop over the number of columns
        for x in range(0, numCols):
            # if our score does not have sufficient probability,
            # ignore it
            if scores_data[x] < min_confidence:
                continue

            # compute the offset factor as our resulting feature
            # maps will be 4x smaller than the input image
            (offsetX, offsetY) = (x * 4.0, y * 4.0)

            # extract the rotation angle for the prediction and
            # then compute the sin and cosine
            angle = angles_data[x]
            cos = np.cos(angle)
            sin = np.sin(angle)

            # use the geometry volume to derive the width and height
            # of the bounding box
            h = x_data0[x] + x_data2[x]
            w = x_data1[x] + x_data3[x]

            # compute both the starting and ending (x, y)-coordinates
            # for the text prediction bounding box
            end_x = int(offsetX + (cos * x_data1[x]) + (sin * x_data2[x]))
            end_y = int(offsetY - (sin * x_data1[x]) + (cos * x_data2[x]))
            start_x = int(end_x - w)
            start_y = int(end_y - h)

            # add the bounding box coordinates and probability score
            # to our respective lists
            rectangles.append((start_x, start_y, end_x, end_y))
            confidences.append(scores_data[x])

    # return a tuple of the bounding boxes and associated confidences
    return rectangles, confidences


def ocr_non_max_suppression(boxes, probs=None, overlap_thresh=0.3):
    # if there are no boxes, return an empty list
    if len(boxes) == 0:
        return []

    # if the bounding boxes are integers, convert them to floats -- this
    # is important since we'll be doing a bunch of divisions
    if boxes.dtype.kind == 'i':
        boxes = boxes.astype('float')

    # initialize the list of picked indexes
    pick = []

    # grab the coordinates of the bounding boxes
    x1 = boxes[:, 0]
    y1 = boxes[:, 1]
    x2 = boxes[:, 2]
    y2 = boxes[:, 3]

    # compute the area of the bounding boxes and grab the indexes to sort
    # (in the case that no probabilities are provided, simply sort on the
    # bottom-left y-coordinate)
    area = (x2 - x1 + 1) * (y2 - y1 + 1)
    idxs = y2

    # if probabilities are provided, sort on them instead
    if probs is not None:
        idxs = probs

    # sort the indexes
    idxs = np.argsort(idxs)

    # keep looping while some indexes still remain in the indexes list
    while len(idxs) > 0:
        # grab the last index in the indexes list and add the index value
        # to the list of picked indexes
        last = len(idxs) - 1
        i = idxs[last]
        pick.append(i)

        # find the largest (x, y) coordinates for the start of the bounding
        # box and the smallest (x, y) coordinates for the end of the bounding
        # box
        xx1 = np.maximum(x1[i], x1[idxs[:last]])
        yy1 = np.maximum(y1[i], y1[idxs[:last]])
        xx2 = np.minimum(x2[i], x2[idxs[:last]])
        yy2 = np.minimum(y2[i], y2[idxs[:last]])

        # compute the width and height of the bounding box
        w = np.maximum(0, xx2 - xx1 + 1)
        h = np.maximum(0, yy2 - yy1 + 1)

        # compute the ratio of overlap
        overlap = (w * h) / area[idxs[:last]]

        # delete all indexes from the index list that have overlap greater
        # than the provided overlap threshold
        idxs = np.delete(idxs, np.concatenate(([last],
                                               np.where(overlap > overlap_thresh)[0])))

    # return only the bounding boxes that were picked
    return boxes[pick].astype('int')


def ocr_resize_boxes(boxes, rw, rh):
    results = []
    for (start_x, start_y, end_x, end_y) in boxes:
        # scale the bounding box coordinates based on the respective
        # ratios
        start_x = int(start_x * rw)
        start_y = int(start_y * rh)
        end_x = int(end_x * rw)
        end_y = int(end_y * rh)

        results.append((start_x, start_y, end_x, end_y))

    return results


def ocr_tesseract(image, boxes, language, padding=0.1):
    h, w = image.shape[:2]

    results = []
    # loop over the bounding boxes
    for (start_x, start_y, end_x, end_y) in boxes:
        # in order to obtain a better OCR of the text we can potentially
        # apply a bit of padding surrounding the bounding box -- here we
        # are computing the deltas in both the x and y directions
        dx = int((end_x - start_x) * padding)
        dy = int((end_y - start_y) * padding)

        # apply padding to each side of the bounding box, respectively
        start_x = max(0, start_x - dx)
        start_y = max(0, start_y - dy)
        end_x = min(w, end_x + (dx * 2))
        end_y = min(h, end_y + (dy * 2))

        # extract the actual padded ROI
        roi = image[start_y:end_y, start_x:end_x]

        # in order to apply Tesseract v4 to OCR text we must supply
        # (1) a language, (2) an OEM flag of 4, indicating that the we
        # wish to use the LSTM neural net model for OCR, and finally
        # (3) an OEM value, in this case, 7 which implies that we are
        # treating the ROI as a single line of text
        try:
            config = '-l {} --oem 1 --psm 7'.format(language)
            text = pytesseract.image_to_string(roi, config=config)
        except pytesseract.TesseractError:
            continue
        except ValueError:
            continue

        # add the bounding box coordinates and OCR's text to the list
        # of results
        results.append(((start_x, start_y, end_x, end_y), text))

    # sort the results bounding box coordinates from top to bottom
    results = sorted(results, key=lambda r: r[0][1])

    return results


def example():
    import os

    # hello message
    print('running example program...')
    print('=' * 50)

    # find data folder based on relative path
    path_base = os.path.dirname(os.path.abspath(__file__))
    path_data = os.path.join(path_base, '..', '..', 'data')
    path_east = os.path.join(path_data, 'frozen_east_text_detection.pb')
    path_ocr_data = os.path.join(path_data, 'text_example', 'ocr')

    # run
    ocr_size = (320, 320)
    ocr_padding = 0.1
    east = load_east_model(path_east)
    for file_name in os.listdir(path_ocr_data):
        print(file_name)
        base_name, ext_name = os.path.splitext(file_name)
        lang = file_name[:base_name.rfind('_')]
        if lang not in {'chinese', 'english', 'japanese', 'korean'}:
            print('unknown language, skipped.')
        else:
            image = Image.open(os.path.join(path_ocr_data, file_name))
            embedded_texts = prepare_ocr_text(image, east, lang, 'eng', ocr_size, ocr_padding)
            print(base_name, ext_name, lang, embedded_texts)
        print('-' * 50)


def main():
    """This script can be directly used in command line.

    If the arguments contain `--example`, the script will run a simple example
    to extract embedded texts in the image. The images should be saved in the
    `data/text_example/ocr` folder. The names should indicate image's language,
    such as `english_1.png` or `chinese_1.jpg`.

    Otherwise, users should set up 3 arguments, namely `--img_name`,
    `--path_layout` and `--path_values`. Another optional argument is
    `--search_range`, and whose default value is `parent`.
    """
    import sys
    args = sys.argv[1:]

    # check example or not
    if '--example' in args:
        example()

    # parse args and run
    else:
        import argparse
        # define path
        path_base = os.path.dirname(os.path.abspath(__file__))
        path_data = os.path.join(path_base, '..', '..', 'data')
        path_east = os.path.join(path_data, 'frozen_east_text_detection.pb')

        # define args parser
        parser = argparse.ArgumentParser()
        parser.add_argument('--path_image', required=True)
        parser.add_argument('--path_east', default=path_east)
        parser.add_argument('--default_lang', default='english')
        parser.add_argument('--fallback_lang', default='english')
        parser.add_argument('--ocr_size', default='320x320')
        parser.add_argument('--ocr_padding', default='0.01')

        # real parameters
        args = parser.parse_args(args)
        image = Image.open(args.path_image)
        east = load_east_model(args.path_east)
        default_lang = args.default_lang
        fallback_lang = args.fallback_lang
        ocr_size = [int(i) for i in args.ocr_size.split('x')]
        ocr_padding = float(args.ocr_padding)

        # extract
        results = prepare_ocr_text(
            image, east, default_lang, fallback_lang, ocr_size, ocr_padding
        )
        print(results)


if __name__ == '__main__':
    main()
