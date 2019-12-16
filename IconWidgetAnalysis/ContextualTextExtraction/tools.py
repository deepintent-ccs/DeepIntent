# -*- coding: UTF-8 -*-

import sys
import zlib
import codecs
import pickle
from io import StringIO
import xml.etree.cElementTree as xmlTree

from PIL import Image


# =========================
# parse xml
# =========================


def parse_xml_string(xml_content):
    """Parse XML and return the root and namespace.

    :param xml_content:
        String, the loaded XML contents.

    :return:
        root: xmlTree object, the root of the xml.
        ns: dict, namespace dictionary.
    """
    ns = []
    root = None
    for event, node in xmlTree.iterparse(
            StringIO(xml_content), events=['start-ns', 'end']):
        if event == 'start-ns':  # namespace
            ns.append(node)
        elif event == 'end':
            root = node

    return root, dict(ns)


# =========================
# image (to save memory)
# =========================


def image_compress(img):
    """Compress the image binaries.

    The aim of this method is to reduce the memory use. This method will also record
    the image size (width, height) and image mode (e.g., RGB, P) to reconstruct it.

    :param img:
        PIL Image, the loaded image object.

    :return:
        img_mode: String;
        img_size: Tuple of int;
        img: The compressed image data.
    """
    img_mode = img.mode
    img_size = img.size
    img = img.tobytes()
    zlib.compress(img)

    return img_mode, img_size, img


def image_decompress(img_mode, img_size, img):
    """Decompress the image.

    Given the compressed image tuple `temp`, the parameter could simply be `*temp`.

    :param img_mode:
        String, image's color mode, such as RGB.
    :param img_size:
        Tuple of int, the size of image, that is (width, height).
    :param img:
        The compressed image data.

    :return:
        image: Image object, which contains image binaries.
    """
    img = Image.frombytes(img_mode, img_size, img)

    return img


def show_compressed_image(compressed_image):
    """Display the compressed image.
    """
    import matplotlib.pyplot as plt

    image_mode, image_size, image = compressed_image
    image = image_decompress(image_mode, image_size, image)

    dpi = 80
    fig_size = image_size[0] / float(dpi), image_size[1] / float(dpi)
    fig = plt.figure(figsize=fig_size)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.axis('off')
    ax.imshow(image, cmap='gray')
    plt.show()


# =========================
# data maintenance
# =========================


def save_python_data(path, data):
    """Save the iterable data in a readable way (python format).

    Each row is stored in a single line to make it easy to view.
    """
    with codecs.open(path, encoding='UTF-8', mode='w') as fo:
        for d in data:
            fo.write(repr(d))
            fo.write('\n')


def load_python_data(path):
    """Load the iterable data line by line.
    """
    data = []
    with codecs.open(path, encoding='UTF-8', mode='r') as fi:
        for line in fi:
            data.append(eval(line))
    return data


def save_pkl_data(path, data):
    """Save data in pkl format (save storage space).
    """
    with open(path, 'wb') as fo:
        pickle.dump(data, fo)


def load_pkl_data(path):
    """Load pkl data from given path.
    """
    with open(path, 'rb') as fi:
        data = pickle.load(fi)
    return data


# =========================
# screen printer
# =========================


class ProcessPrinter:
    """Print the processing status.

    It can run in 3 mode with 3 log levels.
    Silent mode (log_level == 0) print nothing.
    Process mode (log_level == 1) print `.` as the process indicator.
    Verbose mode (log_level == 2) print every data and results.
    """

    def __init__(self, pivot, log_level):
        self.count = 0
        self.pivot = int(pivot)
        self.log_level = log_level

    def update(self, *info):
        self.count += 1
        if self.count > self.pivot:
            if self.log_level == 1:
                print('.', end=' ')
            self.count = 0

        if self.log_level == 2:
            print(*info)

        sys.stdout.flush()

    def finish(self):
        if self.log_level == 1:
            print()
