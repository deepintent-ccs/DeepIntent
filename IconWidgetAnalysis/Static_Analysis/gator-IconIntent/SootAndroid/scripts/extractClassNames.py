#!/bin/env python

import sys,zipfile,os

class GlobalConfigs:
    classPath = ""
    outputFile = ""

def parseJarfiles(classPath):
    """Return a list which contains every jar files"""
    retL = []
    retL = classPath.split(':')
    return retL

def isFileExists(fileName):
    """Return true if the file is exist"""
    if (not fileName.endswith(".jar")):
        return False
    if (os.access(fileName, os.R_OK)):
        return True
    else:
        return False;

def listContentInZip(fileName):
    """Return a list, every line contains a name of classfile"""
    if (not isFileExists(fileName)):
        return []
    curJar = zipfile.ZipFile(fileName, "a")
    curList = curJar.namelist()
    retList = []
    for item in curList:
        if '.class' in item:
            item = item[:item.index('.')]
            retList.append(item.replace('/','.'))
    return retList

def writeClassNamesToFile(fileName, classNameSet):
    """Write the class names to file"""
    with open(fileName, "w") as outF:
        for item in classNameSet:
            outF.write(item + "\n")
            pass
        pass
    pass

def main():
    if (len(sys.argv) < 3):
        print("Usage: python extractClassNames.py CLASS_PATH OUTPUT")
        sys.exit(-1)
    GlobalConfigs.classPath = sys.argv[1]
    GlobalConfigs.outputFile = sys.argv[2]
    jarList = parseJarfiles(GlobalConfigs.classPath)
    classNameSet = set()
    for fileName in jarList:
        if (isFileExists(fileName)):
            retL = listContentInZip(fileName)
            for className in retL:
                classNameSet.add(className)
                pass
            pass
        pass
    writeClassNamesToFile(GlobalConfigs.outputFile, classNameSet)
    pass


if __name__ == '__main__':
    main()
