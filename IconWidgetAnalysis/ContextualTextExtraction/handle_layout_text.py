# -*- coding: UTF-8 -*-

import os
import codecs

from tools import parse_xml_string


def handle_layout_text(app_name, img_name, layout, path_decoded, search_range):
    """Extract texts from layout file.

    The method will parse the layout file and extract contextual texts of
    the image. Based on the `search_range` parameter, the method will treat
    texts that are contained in the parent layout or the whole layout file
    as contextual texts.

    Moreover, the method will also dereference `@string` symbols by searching
    the `res/values/strings.xml`.

    :param app_name:
        String, the App name, to locate the decoded folder.
    :param img_name:
        String, the image name, to locate the image in the layout xml.
    :param layout:
        String, the layout file name, the image is contained in the layout.
    :param path_decoded:
        String, path of decoded Apps, must contains the target App.
    :param search_range:
        String, define the extract range for layout texts, could be `parent`
        (only extract texts in the same parent layout) or `total` (extract
        texts in the whole layout file).

    :return:
        text: List, contains several words or empty if not found.
    """

    # app path and resource path
    path_app = os.path.join(path_decoded, app_name)
    assert os.path.exists(path_app)
    path_res = os.path.join(path_app, 'res')

    # strings path and layout path
    path_layout = get_resource_path(path_res, 'layout', layout)
    path_values = get_resource_path(path_res, 'values', 'strings.xml')

    return handle_layout_text_from_xml(img_name, path_layout, path_values, search_range)


def handle_layout_text_from_xml(img_name, path_layout, path_string_values, search_range):
    """Extract layout texts based on the given xml file.

    :param img_name:
        String, the image name, to locate the image in the layout xml.
    :param path_layout:
        String, path of layout file, could be None (However, the method will return
        an empty list).
    :param path_string_values:
        String, path of string file, which contains the dereference values of string
        ids/symbols, could be None (also means to disable the dereference process).
    :param search_range:
        String, define the textual extraction range, could be `parent` (only extract
        texts in the same parent layout) or `total` (extract texts in the whole layout
        file).

    :return:
        text: List, contains several words or empty if not found.
    """
    # load strings.xml
    if path_string_values is not None:
        string_content = codecs.open(path_string_values, encoding='UTF-8', mode='r').read()
        string_dict = get_values_string(string_content)
    else:
        string_dict = {}

    # get texts from layout
    texts = []
    if path_layout is not None:
        layout_content = codecs.open(path_layout, encoding='UTF-8', mode='r').read()
        layout_texts = get_text_from_layout(img_name, layout_content, string_dict, search_range)
        texts.extend(layout_texts)

    return texts


def get_parent_nodes(root, img_name):
    """Find the parent nodes in the xml tree.

    This method will first find the target node based on the
    `img_name`. Then, if the target image node is found, the
    method will find all the parent nodes and return a List.
    Otherwise, the method will fallback to the `total` search
    range and return the root node.

    :param root:
        XML element object, the root of the XML tree.
    :param img_name:
        String, the image name.

    :return:
        rs: List of Elements, parent nodes if the target image
        is found, or the root node if not found.
    """
    # find target image node
    tk, tv = None, None
    t_attr_value = '@drawable/' + img_name
    for node in root.iter():
        for k, v in node.attrib.items():
            if v == t_attr_value:
                tk, tv = k, v
                break

    # find parent node
    if tk is not None:
        ts = './/*[@{}="{}"]/..'.format(tk, tv)
        rs = root.findall(ts)
        assert isinstance(rs, list)
        return rs
    return [root]


def get_text_from_layout(img_name, layout_content, string_dict, search_range):
    """Extract layout texts from xml descriptions.

    The method will go through the parent nodes, and collect all
    the visible texts. The method will also dereference the string
    resources (starts with `@string`) to real texts.

    :param img_name:
        String, image name.
    :param layout_content:
        String, loaded layout xml.
    :param string_dict:
        Dict, mapping string resources to real texts.
    :param search_range:
        String, `parent` or `total`.

    :return:
        texts: List of string, or empty list if nothing found.
    """
    texts = []
    root, ns = parse_xml_string(layout_content)

    parents = get_parent_nodes(root, img_name) if search_range == 'parent' else [root]
    text_attrib = '{{{}}}text'.format(ns['android'])
    vis_attrib = '{{{}}}visibility'.format(ns['android'])
    for parent in parents:
        for node in parent.iter():
            # skip invisible text
            try:
                if node.attrib[vis_attrib] == 'invisible':
                    continue
            except KeyError:
                pass

            # extract texts
            try:
                text = node.attrib[text_attrib]
                if text.startswith('@string'):
                    # map identifier to real contents (described in strings.xml)
                    text = string_dict[text.split('/', 1)[1]]
                if text is not None:
                    texts.append(text)
            except KeyError:
                continue

    return texts


def get_values_string(string_content):
    """Parse and collect string values.

    Android uses `string` element to define string resources, this
    method will find all the `string` elements and produce a name
    to real string mapping.

    :param string_content:
        String, the xml contents, to be parsed in this method.

    :return:
        values_string: Dict, mapping name to real text.
    """
    values_string = {}
    root, ns = parse_xml_string(string_content)
    for target in root.findall('string', ns):
        values_string[target.attrib['name']] = target.text

    return values_string


def get_resource_path(path_res, prefix, content_name):
    """Get the `content_name` resource in the folder whose name starts
    with the `prefix`.

    :param path_res:
        String, path of resource folder, which defines the searching
        space.
    :param prefix:
        String, the parent folder's prefix. Note that, there might be
        several `layout` folders to suit different size of phone screen
        or any other different situations, such as `layout-800x400`,
        `layout-land` folder.
    :param content_name:
        String, target resource file name.

    :return:
        path: String, if find the resource file. Or None if failed.
    """
    for name in os.listdir(path_res):
        if name.startswith(prefix):
            fp_temp = os.path.join(path_res, name, content_name)
            if os.path.exists(fp_temp):
                return fp_temp
    return None


def example():
    import os

    # hello message
    print('running example program...')
    print('=' * 50)

    # find data folder based on relative path
    path_base = os.path.dirname(os.path.abspath(__file__))
    path_data = os.path.join(
        path_base, '..', '..', 'data', 'text_example', 'layout'
    )

    # meta data, uses relative path
    #   Format: [[img_name, path_layout, path_values, search_range], ...]
    meta_data = [
        # common case
        ['button_swap', 'list_item_location.xml', 'list_item_location.strings.xml', 'parent'],
        # image node not found (loaded by code not layout)
        ['drawer_down_sel', 'untangle.xml', 'untangle.strings.xml', 'parent'],
        # different texts for different search range
        ['map_logout', 'main_map_layout.xml', 'main_map_layout.strings.xml', 'parent'],
        ['map_logout', 'main_map_layout.xml', 'main_map_layout.strings.xml', 'total'],
    ]

    # run
    for i in range(len(meta_data)):
        d = meta_data[i]
        path_layout = os.path.join(path_data, d[1])
        path_values = os.path.join(path_data, d[2])
        r = handle_layout_text_from_xml(
            d[0], path_layout, path_values, d[3]
        )
        print('meta:', d)
        print('result:', r)
        print('-' * 50)


def main():
    """This script can be directly used in command line.

    If the arguments contain `--example`, the script will run a simple
    example to extract layout texts stored in `data/text_example/layout`
    folder with fixed meta data (defined in the `example` function).

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
        parser = argparse.ArgumentParser()
        parser.add_argument('--img_name', required=True)
        parser.add_argument('--path_layout', required=True)
        parser.add_argument('--path_values', required=True)
        parser.add_argument('--search_range', required=False, default='parent')

        args = parser.parse_args(args)
        results = handle_layout_text_from_xml(
            args.img_name,
            args.path_layout, args.path_values,
            args.search_range
        )
        print(results)


if __name__ == '__main__':
    main()
