# -*- coding: UTF-8 -*-


def handle_resource_text(resource_name):
    """Split resource names.

    This method considers Under-Score-Case variables, Camel-Case
    variables and the combination of them.
    In detail, the method will first handle the Under-Score-Case,
    and the the Camel-Case.

    :param resource_name:
        String, resource name to be process, e.g., image name.

    :return:
        texts: List of string, the divided names.
    """
    texts = []
    for var_name in split_under_score_case(resource_name):
        texts.extend(split_camel_case(var_name))
    return texts


def split_under_score_case(var_name):
    """Split Under-Score-Case variable names.

    In detail, this method only split the text with '_'.

    :param var_name:
        String, the variable name.

    :return:
        texts: List of string.
    """
    return var_name.split('_')


def split_camel_case(var_name):
    """Split Camel-Case variable names.

    In detail, this method will cut the text when the character
    switch from lower to upper.

    :param var_name:
        String, the variable name.

    :return:
        r: List of string, the split texts.
    """
    r = []
    li = -2  # lower index
    ui = 0  # upper index
    pi = 0  # prev truncate index
    for i in range(len(var_name)):
        if var_name[i].islower():
            li = i
        elif var_name[i].isupper():
            ui = i

        if li + 1 == ui:
            r.append(var_name[pi: ui])
            pi = ui

    r.append(var_name[pi:])
    return r


def example():
    # hello message
    print('running example program...')
    print('=' * 50)

    # example data
    data = [
        'hello',
        'ABC',
        'aRGB',
        'tempR',
        'ForeignExchange',
        'PreciousMetal',
        'SimIMC',
        'Music',
        'idTextView',
        'ScalingStability_TestRoot',
        'btn_GoBack',
    ]

    # run
    for i in range(len(data)):
        d = data[i]
        r = handle_resource_text(d)
        print('{} -> {}'.format(d, r))
        print('-' * 50)


def main():
    """This script can be directly used in command line.

    If the arguments contain `--example`, the script will run a simple
    example to split resource names, the example data is defined in the
    `example` function.

    Otherwise, the method will split each inputted arguments.
    """
    import sys
    args = sys.argv[1:]

    # check example or not
    if '--example' in args:
        example()

    # parse args and run
    else:
        for arg in args:
            r = handle_resource_text(arg)
            print('{} -> {}'.format(arg, r))


if __name__ == '__main__':
    main()
