import os

apps = list()
f = open("selectedAPK.txt", "r")
for line in f.readlines():
	line = line.strip("\n")
	apps.append(str(line))

d = "/Users/shaoyang/Downloads/test/testapks/"
os.chdir(d)
files = os.listdir(d)
for file in files:
	if (str(file)[0:32]) in apps:
		continue
	else:
		os.remove(file)
	