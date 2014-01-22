#!/bin/bash

set -eu
# Invokes 'java' using .class files as compiled by Eclipse, plus the scala library installed by sbt.

CP=target/scala-2.10/classes
CP=$HOME/.sbt/boot/scala-2.10.3/lib/scala-library.jar:$CP

# file.encoding necessary for mac (ugh!!) http://stackoverflow.com/questions/361975/setting-the-default-java-character-encoding

exec java -Dfile.encoding=UTF-8 -XX:ParallelGCThreads=2 -Xmx2g -cp "$CP" "$@"
