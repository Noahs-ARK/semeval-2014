#!/bin/bash

set -eu

# Invokes 'java' using .class files as compiled by SBT and/or Eclipse,
# plus flags to stop crashes due to idiotic default JVM behaviors

CP=target/scala-2.10/classes
CP=$HOME/.sbt/boot/scala-2.10.3/lib/scala-library.jar:$CP

# Local jars
CP=lib/sdp.jar:$CP
CP=lib/myutil.jar:$CP
CP=lib/kryo-2.22-all.jar:$CP
CP=lib/commons-lang3-3.2.1.jar:$CP

# file.encoding necessary for mac (ugh!!) http://stackoverflow.com/questions/361975/setting-the-default-java-character-encoding
# XX:ParallelGCThreads prevents horrible crashes on large multicore machines (basically, another stupid java bug)

klass=$1
shift
klass=edu.cmu.cs.ark.semeval2014.$klass

# set -x
exec java -ea -Dfile.encoding=UTF-8 -XX:ParallelGCThreads=2 -Xmx4g -cp "$CP" $klass "$@"
