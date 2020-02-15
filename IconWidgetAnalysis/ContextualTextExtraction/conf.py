# -*- coding: UTF-8 -*-

import os
import argparse
from collections import namedtuple

ExtractionConf = namedtuple('TextExtractConf', [
    # path
    'path_pa',              # String, path of program analysis results, could be '.csv' or zipped '.csv'.
    'path_app',             # String, path of decoded Apps.
    'path_east',            # String, path of pre-trained EAST model.
    'path_save',            # String, path to save the extracted data.
    # log
    'log_level',            # Int, decide to print how much information to console, could be '0' (silent
                            #      mode, print nothing), '1' (simple mode, only print '.' to indicate the
                            #      progress) or '2' (verbose mode, print all the intermediate results).
    # layout text extraction
    'layout_text_range',    # String, define the extract range for layout texts, could be 'parent' (only
                            #         extract texts in the same parent layout) or 'total' (extract texts
                            #         in the whole layout file).
    # embedded text extraction
    'ocr_width',            # Int, width of the image to be inputted in the ocr process.
    'ocr_height',           # Int, height of the image to be inputted in the ocr process.
    'ocr_padding',          # Float, padding of the text bounding box, 0.05 or 0.1 seems to be suitable.
    'enable_ocr_cache',     # Boolean, whether to record and lookup results of extracted image.
    # translation
    'enable_translate',        # Boolean, whether to translate the extracted texts into english.
    'enable_translate_cache',  # Boolean, whether to record and lookup results of translated texts.
])


def check_conf(value, domain, fallback):
    """Keep the conf value within the specific domain.
    """
    return value if value in domain else fallback


class ExtractionConfArgumentParser:
    """Generate ExtractionConf object from args.
    """

    @staticmethod
    def add_bool_arg(parser, name, default):
        group = parser.add_mutually_exclusive_group(required=False)
        group.add_argument('--' + name, dest=name, action='store_true')
        group.add_argument('--no_' + name, dest=name, action='store_false')
        parser.set_defaults(**{name: default})

    def __init__(self, desc='Generate extraction configuration from args.'):
        self.parser = argparse.ArgumentParser(description=desc)
        self.parser.add_argument('--path_pa', type=str, required=True)
        self.parser.add_argument('--path_app', type=str, required=True)
        self.parser.add_argument('--path_east', type=str, required=True)
        self.parser.add_argument('--path_save', type=str, required=True)
        self.parser.add_argument('--log_level', type=int, default=1)
        self.parser.add_argument('--layout_text_range', type=str, default='parent')
        self.parser.add_argument('--ocr_width', type=int, default=320)
        self.parser.add_argument('--ocr_height', type=int, default=320)
        self.parser.add_argument('--ocr_padding', type=float, default=0.05)
        self.add_bool_arg(self.parser, name='ocr_cache', default=True)
        self.add_bool_arg(self.parser, name='translate', default=True)
        self.add_bool_arg(self.parser, name='translate_cache', default=True)

    def parse(self, args):
        args = self.parser.parse_args(args)
        return ExtractionConf(
            path_pa=args.path_pa, path_app=args.path_app,
            path_east=args.path_east, path_save=args.path_save,
            log_level=args.log_level,
            layout_text_range=args.layout_text_range,
            ocr_width=args.ocr_width, ocr_height=args.ocr_height,
            ocr_padding=args.ocr_padding, enable_ocr_cache=args.ocr_cache,
            enable_translate=args.translate,
            enable_translate_cache=args.translate_cache
        )
