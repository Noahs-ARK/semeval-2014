#!/bin/bash

set -eu

# Invokes 'java' using .class files as compiled by Eclipse, plus the scala library installed by sbt,
# plus flags to stop crashes due to idiotic default JVM behaviors

CP=target/scala-2.10/classes
CP=$HOME/.sbt/boot/scala-2.10.3/lib/scala-library.jar:$CP

# Local jars
CP=lib/sdp.jar:$CP
CP=lib/myutil.jar:$CP

# file.encoding necessary for mac (ugh!!) http://stackoverflow.com/questions/361975/setting-the-default-java-character-encoding
# XX:ParallelGCThreads prevents horrible crashes on large multicore machines (basically, another stupid java bug)

exec java -ea -Dfile.encoding=UTF-8 -XX:ParallelGCThreads=2 -Xmx2g -cp "$CP" "$@"
