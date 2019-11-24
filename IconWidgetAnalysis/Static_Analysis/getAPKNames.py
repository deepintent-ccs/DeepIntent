import os
import sys

def loadFile():
	dirPath = sys.argv[1]
	files = os.listdir(dirPath)
	for i in range(len(files)):
		if ".apk.json" in files[i]:
			getNames(str(dirPath), str(files[i]))

def getNames(inputDir, fileName):
	index = fileName.rindex(".apk.json")
	fileName = fileName[0:index]
	print(fileName)
	f = open("selectedAPK.txt", "a")
	f.write(fileName + "\n")

def main():
	loadFile()

if __name__ == "__main__":
	main()