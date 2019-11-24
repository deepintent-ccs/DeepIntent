import os
import csv

inputf = "/Users/shaoyang/Desktop/permissions.csv"
inputf2 = "/Users/shaoyang/Desktop/1tomore.txt"
outputf = "/Users/shaoyang/Desktop/outputP.csv"
methods = dict()
count = 0

fin = csv.reader(open(inputf, "r"))
fin2 = open(inputf2, "r")
fout = csv.writer(open(outputf, "w"))



for line in fin2.readlines():
	line = line.strip("\n").rstrip("\t").split("\t")
	temp = list()
	for i in range(1, len(line)):
		temp.append(str(line[i]))
	methods[str(line[0])] = temp

for line in fin:
	count += 1
	print(count)
	if str(line[6]) in methods.keys():
		line[7] = methods[line[6]]
		print("yes")
		fout.writerow(line)
	else:
		print("no")
		fout.writerow(line)







	
		

