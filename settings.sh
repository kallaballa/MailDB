#!/bin/bash

MDBDIR=`dirname $(readlink -f $0)`
MDBLIB="$MDBDIR/lib"
MDBBIN="$MDBDIR/bin"
MDBSRC="$MDBDIR/src"
CLASSPATH="`ls $MDBLIB/*.jar | while read jar; do echo -n "$jar:"; done`"
CLASSPATH="$CLASSPATH:`cd $MDBSRC; \
find . -name "*.java" -exec dirname '{}' \; | sort | uniq | \
  while read dir; do echo ":$MDBBIN/$dir"; done;`"
