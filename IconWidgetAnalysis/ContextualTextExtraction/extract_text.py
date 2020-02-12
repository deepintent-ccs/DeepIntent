# -*- coding: UTF-8 -*-

from conf import check_conf
from tools import save_pkl_data, ProcessPrinter
from load_data import load_data
from handle_layout_text import handle_layout_text
from handle_embedded_text import extract_drawable_image, load_east_model, extract_embedded_text
from handle_resource_text import handle_resource_text


def extract_contextual_texts(data, drawable_images, path_app, path_east,
                             search_range='parent',
                             ocr_size=(320, 320), ocr_padding=0.1, enable_ocr_cache=True,
                             is_translate=True, enable_translate_cache=True,
                             log_level=0):
    # extract layout texts
    print('extracting layout texts')
    layout_texts = extract_layout_texts(data, path_app, search_range, log_level)

    # extract embedded texts
    print('extracting embedded texts')
    app2lang = get_app2lang(data, layout_texts)
    east_model = load_east_model(path_east)
    embedded_texts = extract_embedded_texts(data, drawable_images, app2lang, east_model,
                                            ocr_size, ocr_padding, enable_ocr_cache, log_level)

    # translate layout texts and embedded texts
    if is_translate:
        print('translating')
        layout_texts = translate_texts(data, layout_texts, enable_translate_cache, log_level)
        embedded_texts = translate_texts(data, embedded_texts, enable_translate_cache, log_level)

    # extract resource texts
    print('extracting resource texts')
    resource_texts = extract_resource_texts(data, log_level)

    # merge extracted texts
    assert len(data) == len(layout_texts) == len(embedded_texts) == len(resource_texts)
    results = [[layout_texts[i], embedded_texts[i], resource_texts[i]] for i in range(len(data))]
    return results


def extract_drawable_images(data_pa, path_app, log_level=0):
    """Extract drawable images for each (app, img, layout) tuple.

    :param data_pa:
        List, each row contains (app, img, layout, permissions) tuple.
    :param path_app:
        String, the path of the decoded Apps.
    :param log_level:
        Int,

    :return:
        List,
    """
    results = []
    log_helper = ProcessPrinter(len(data_pa) / 20, log_level)
    for app_name, img_name, layout, _ in data_pa:
        result, result_path, result_traces = extract_drawable_image(app_name, img_name, path_app)
        results.append(result)
        log_helper.update('[image]', app_name, img_name, layout, ':',
                          (result[0], result[1]) if result is not None else result)
    log_helper.finish()

    return results


def extract_layout_texts(data_pa, path_app, search_range, log_level=0):
    results = []
    log_helper = ProcessPrinter(len(data_pa) / 20, log_level)
    for app_name, img_name, layout, _ in data_pa:
        result = handle_layout_text(app_name, img_name, layout, path_app, search_range)
        results.append(result)
        log_helper.update('[layout]', app_name, img_name, layout, ':', result)
    log_helper.finish()

    return results


def get_app2lang(data_pa, layout_texts):
    from translate_text import check_default_language

    # collect all the layout texts appeared in the app
    app_texts = {}  # app -> all the layout texts
    for i in range(len(data_pa)):
        app_name = data_pa[i][0]
        if app_name not in app_texts:
            app_texts[app_name] = []
        app_texts[app_name].extend(layout_texts[i])

    app2lang = {app_name: check_default_language(texts) for app_name, texts in app_texts.items()}
    return app2lang


def extract_embedded_texts(data_pa, drawable_images, app2lang, east_model,
                           ocr_size, ocr_padding, enable_cache=True, log_level=0):
    results = []
    log_helper = ProcessPrinter(len(data_pa) / 20, log_level)
    for i in range(len(data_pa)):
        app_name, img_name, layout, _ = data_pa[i]
        result = extract_embedded_text(app_name, img_name, drawable_images[i], east_model,
                                       app2lang[app_name], 'english',
                                       ocr_size, ocr_padding, enable_cache)
        results.append(result)
        log_helper.update('[embedded]', app_name, img_name, layout, ':', result)
    log_helper.finish()

    return results


def translate_texts(data_pa, texts, enable_cache=True, log_level=0):
    from translate_text import translate_any_to_english
    assert len(data_pa) == len(texts)

    results = []
    log_helper = ProcessPrinter(sum([len(t) for t in texts]) / 20, log_level)
    for i in range(len(data_pa)):
        app_name, img_name, layout, _ = data_pa[i]
        translated = []
        for t in texts[i]:
            r = translate_any_to_english(t, enable_cache)
            translated.append(r)
            log_helper.update('[translate]', app_name, img_name, layout, ':', t, '->', r)
        results.append(translated)
    log_helper.finish()

    return results


def extract_resource_texts(data_pa, log_level=0):
    results = []
    log_helper = ProcessPrinter(len(data_pa) / 20, log_level)
    for app_name, img_name, layout, _ in data_pa:
        result = handle_resource_text(img_name)
        results.append(result)
        log_helper.update('[res]', app_name, img_name, layout, ':', result)
    log_helper.finish()

    return results


def execute_with_conf(conf):
    # load program analysis results, format: app, image, layout, permissions
    print('loading program analysis results')
    data_pa = load_data(conf.path_pa)

    # extract drawable images
    print('extracting drawable images')
    log_level = check_conf(conf.log_level, {0, 1, 2}, 0)
    drawable_images = extract_drawable_images(data_pa, conf.path_app, conf.log_level)

    # extract texts, format: layout_texts, embedded_texts, resource_texts
    print('extracting texts')
    search_range = check_conf(conf.layout_text_range, {'parent', 'total'}, 'parent')
    enable_ocr_cache = check_conf(conf.enable_ocr_cache, {True, False}, True)
    is_translate = check_conf(conf.enable_translate, {True, False}, True)
    enable_translate_cache = check_conf(conf.enable_translate_cache, {True, False}, True)
    texts = extract_contextual_texts(data_pa, drawable_images, conf.path_app, conf.path_east,
                                     search_range,
                                     (conf.ocr_width, conf.ocr_height), conf.ocr_padding, enable_ocr_cache,
                                     is_translate, enable_translate_cache,
                                     log_level)

    # merge and save the triple, <image, texts, permissions>
    print('finished and save')
    assert len(data_pa) == len(drawable_images) == len(texts)
    # format: [app, image, layout, {permissions}, (compressed_img), [[layout_texts], [embedded_texts], [res_texts]]]
    # format: [(compressed_img), [[layout_texts], [embedded_texts], [res_texts]], {permissions}]
    data = [[drawable_images[i]] + [texts[i]] + [data_pa[i][-1]] for i in range(len(data_pa))]
    save_pkl_data(conf.path_save, data)


def example():
    import os
    from conf import ExtractionConf

    benign_conf = ExtractionConf(
        # path
        path_pa=os.path.join('..', '..', 'data', 'example', 'example_result_widget_permission_mapping_benign.csv.zip'),
        path_app='H:' + os.path.sep + os.path.join('dataset', 'AppsIcon', 'benign_decoded'),
        path_east=os.path.join('..', '..', 'data', 'frozen_east_text_detection.pb'),
        path_save=os.path.join('..', '..', 'data', 'example', 'raw_data.benign.pkl'),
        # log
        log_level=1,
        # layout text extraction
        layout_text_range='parent',
        # embedded text extraction
        ocr_width=320,
        ocr_height=320,
        ocr_padding=0.05,
        enable_ocr_cache=True,
        # translation
        enable_translate=True,
        enable_translate_cache=True
    )

    malicious_conf = ExtractionConf(
        # path
        path_pa=os.path.join('..', '..', 'data', 'example',
                             'example_result_widget_permission_mapping_malicious.csv.zip'),
        path_app='H:' + os.path.sep + os.path.join('dataset', 'AppsIcon', 'malicious_decoded'),
        path_east=os.path.join('..', '..', 'data', 'frozen_east_text_detection.pb'),
        path_save=os.path.join('..', '..', 'data', 'example', 'raw_data.mal.pkl'),
        # log
        log_level=1,
        # layout text extraction
        layout_text_range='parent',
        # embedded text extraction
        ocr_width=320,
        ocr_height=320,
        ocr_padding=0.05,
        enable_ocr_cache=True,
        # translation
        enable_translate=True,
        enable_translate_cache=True
    )

    print('benign')
    execute_with_conf(benign_conf)
    print('malicious')
    execute_with_conf(malicious_conf)


def main():
    import sys
    args = sys.argv[1:]

    # check example or not
    if '--example' in args:
        from conf import example_conf
        execute_with_conf(example_conf)

    # example()


if __name__ == '__main__':
    main()
