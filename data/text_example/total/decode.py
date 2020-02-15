import os

path_apk = 'apk'
path_decode = 'apk_decoded'

if not os.path.exists(path_decode):
    os.mkdir(path_decode)

for filename in os.listdir(path_apk):
    apk_name, ext_name = os.path.splitext(filename)
    print(apk_name)

    path_in = os.path.join(path_apk, filename)
    path_out = os.path.join(path_decode, apk_name)
    os.system('apktool d {} -o {}'.format(path_in, path_out))
