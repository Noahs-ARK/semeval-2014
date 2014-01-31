#!/bin/bash

set -eu

# Invokes 'java' using .class files as compiled by SBT and/or Eclipse,
# plus flags to stop crashes due to idiotic default JVM behaviors

# file.encoding necessary for mac (ugh!!) http://stackoverflow.com/questions/361975/setting-the-default-java-character-encoding
# TODO: should set encoding explicitly in the java/scala code
# XX:ParallelGCThreads prevents horrible crashes on large multicore machines (basically, another stupid java bug)
# -ea enables assertions

# so we don't have to type out "edu.cmu.cs.ark.semeval2014." every time
klass="edu.cmu.cs.ark.semeval2014.$1"
shift

# make sure your bin/sbt doesn't override these JAVA_OPTS, or just include these in bin/sbt
export JAVA_OPTS="-ea -Dfile.encoding=UTF-8 -XX:ParallelGCThreads=2 -Xmx4g"
export SBT_OPTS=$JAVA_OPTS

set -x
$(dirname $0)/scripts/sbt "run-main $klass $*"
## alternatively, run "sbt assembly" then you can put the uberjar on the classpath like below
# java ${JAVA_OPTS} -cp target/scala-2.10/semeval2014-assembly-0.1-SNAPSHOT.jar $klass $@
