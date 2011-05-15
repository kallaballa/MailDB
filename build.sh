#!/bin/bash

. `dirname $0`/settings.sh

export CLASSPATH
javac -d $MDBBIN $MDBSRC/*.java
[ $? == 0 ] && echo "done" || echo "failed"

