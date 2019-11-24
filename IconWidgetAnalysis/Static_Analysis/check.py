import os

path = "/Users/shaoyang/Downloads/test/output/img2widgets/"
files = os.listdir(path)
count = 0

for file in files:
	f = open(path + str(file), "r")
	for line in f.readlines():
		if ("[" in line) & ("]" in line):
			in1 = line.index("[")
			in2 = line.index("]")
			if ((in2 - in1) != 1):
				print(file)
				count += 1
				break
		else:
			continue
print(count)
