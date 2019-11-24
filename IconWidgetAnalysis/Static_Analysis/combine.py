import csv
import os
import sys

output = open("./permissions.csv", "w")
header = ["APK", "Image", "WID", "WID Name", "Layout", "Handler", "Method", "Permissions"]
writer = csv.DictWriter(output, fieldnames = header)
writer.writeheader()

#dirPath = "/Users/shaoyang/Downloads/test/permission_output/"
dirPath = sys.argv[1]
files = os.listdir(dirPath)
for i in range(len(files)):
	if ".csv" in files[i]:
		print(files[i])
		f = open(dirPath + files[i], "r")
		reader = csv.reader(f, delimiter = "\t")
		rowNum = 0
		for row in reader:
			if (rowNum == 0):
				rowNum += 1
				continue;
			else:
				writer.writerow({"APK": row[0], "Image": row[1], "WID": row[2], "WID Name": row[3], "Layout": row[4], "Handler": row[5], "Method": row[6], "Permissions": row[7]})
			rowNum += 1

