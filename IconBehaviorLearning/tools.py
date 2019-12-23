# -*- coding: UTF-8 -*-

import zlib
import codecs
import pickle

from PIL import Image


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
