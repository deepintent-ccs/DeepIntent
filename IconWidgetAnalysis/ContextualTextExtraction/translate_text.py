# -*- coding: UTF-8 -*-

from collections import Counter

# =========================
# language detection
# =========================
# utf-8 range
# \u0000-\u007f (english)
# \u4e00-\u9fa5, \u3400-\u4db5 (chinese)
# \u3130-\u318F, \uac00-\ud7a3 (korean)
# \u0800-\u4e00 (japanese)


def check_contain_chinese(check_str):
    """Check any chinese character contained in the given string.
    """
    return any('\u4e00' <= ch <= '\u9fff' for ch in check_str)


def check_contain_japanese(check_str):
    """Check any japanese character contained in the given string.
    """
    return any('\u0800' <= ch <= '\u4e00' or
               '\u3400' <= ch <= '\u4db5' for ch in check_str)


def check_contain_korean(check_str):
    """Check any korean character contained in the given string.
    """
    return any('\u3130' <= ch <= '\u318f' or
               '\uac00' <= ch <= '\ud7a3' for ch in check_str)


def check_contain_alpha(check_str):
    """Check any word (or not symbol) contained in the given string.
    """
    return any(ch.isalpha() for ch in check_str)


def check_ascii_only(check_str):
    """Check all the words are ascii words (english words).
    """
    return all(ch <= '\u007f' for ch in check_str if ch.isalpha())


def language_detection(check_str):
    """Detect language in a simple and non-complete way.

    It is difficult to detect the language of the given string exactly correctly,
    especially when the string is a fix of different languages. Currently, this
    method only consider 4 languages, namely Chinese, Japanese, Korean and English.
    Each language is detected by its Unicode index range.

    The mapping is as follows.
    Chinese: \\u4e00-\\u9fa5, \\u3400-\\u4db5.
    Korean: \\u3130-\\u318F, \\uac00-\\ud7a3.
    Japanese: \\u0800-\\u4e00.

    Note that, the method return `None` if there are no words in the given string.
    Moreover, the fallback language is English, which means if the method do not
    understand the given string, it will return English.

    :param check_str:
        String, the given string to detect.
    :return:
        lang: String, the language name or `None` if no word.
    """
    if not check_contain_alpha(check_str):
        return None
    elif check_contain_chinese(check_str):
        return 'chinese'
    elif check_contain_japanese(check_str):
        return 'japanese'
    elif check_contain_korean(check_str):
        return 'korean'
    else:
        return 'english'


def check_default_language(texts):
    """
    Check each given text's language, and default language is the most frequent language.
    Notice: 1) None character text will be ignore; 2) Fallback language is english.
    """
    lang_counter = Counter()
    for text in texts:
        lang = language_detection(text)
        if lang is not None:
            lang_counter.update([lang])

    if len(lang_counter) >= 1:
        return lang_counter.most_common(1)[0][0]
    else:
        return 'english'


# =========================
# translation
# =========================


def translate_any_to_english(text, enable_cache=True):
    """Translate any other language into English.

    This method uses `translate` package to translate any other language into
    English. Please first use `pip install translate` to install this package.

    The `translate` package uses online APIs, which 1) limits the number of
    translations per IP per day and 2) might return error messages instead of
    the translation results.

    For the first problem, we have skipped the English only texts and enabled
    translation cache to reduce the translation times. But if your data set is
    really big, the only way might be split the data set and translate them in
    several days or through different PCs (with different IP).

    For the second problem, we try to lower the target string before translation
    and check the returned results contains upper characters or not. If true,
    which means there might be some error when translate the given string, and
    the method will return the empty string.

    :param text:
        String, the target string to be translated.
    :param enable_cache:
        Boolean, the method will return the cached results if enabled.

    :return:
        result: String, the translated text or empty string if failed.
    """
    from translate import Translator

    # initialize
    if not hasattr(translate_any_to_english, 'worker'):
        translate_any_to_english.worker = Translator(
            from_lang='autodetect', to_lang='english'
        )
        translate_any_to_english.cache = {}  # text -> result
    worker = translate_any_to_english.worker
    cache = translate_any_to_english.cache

    # return original text if text only contains ascii character
    if check_ascii_only(text):
        return text

    # check the cache
    if enable_cache and text in cache:
        return cache[text]

    # translate
    result = ''
    is_failed = False
    try:
        text_lower = text.lower()
        result = worker.translate(text_lower)

        # the translator report error message by returning the
        # sentence that all the characters are in upper case,
        # such as 'PLEASE SELECT TWO DISTINCT LANGUAGES'
        if result.isupper():
            is_failed = True
        # sometimes, the translation might fail and return
        # the original text or something strange, so we also
        # check the translation result to guarantee the quality
        elif not check_ascii_only(result):
            is_failed = True
    except:
        # not stable, due to network error,
        # too long text or something else
        is_failed = True

    # return
    if not is_failed:
        # update the cache
        cache[text] = result
        return result
    else:
        # translate failed, return empty string
        return ''


def example():
    # hello message
    print('running example program...')
    print('=' * 50)

    # example data
    data = [
        'hello, world',
        '你好，世界',
        '世界、こんにちは',
        '전 세계 여러분 안녕하세요',
    ]

    # run
    for i in range(len(data)):
        d = data[i]
        r = translate_any_to_english(d)
        print('{} -> {}'.format(d, r))
        print('-' * 50)


def main():
    """This script can be directly used in command line.

    If the arguments contain `--example`, the script will run a simple
    example to split resource names, the example data is defined in the
    `example` function.

    Otherwise, the method will translate all the following texts.
    """
    import sys
    args = sys.argv[1:]

    # check example or not
    if '--example' in args:
        example()

    # run with all args as a sentence
    else:
        s = ' '.join(args)
        r = translate_any_to_english(s)
        print('{} -> {}'.format(s, r))


if __name__ == '__main__':
    main()
