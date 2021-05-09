#!/bin/sh

cd src 
find -name "*.java" > sources.txt
rm -rf build
mkdir -p build
javac -d build @sources.txt
rm sources.txt
cd ..
