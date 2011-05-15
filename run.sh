#!/bin/bash

. `dirname $0`/settings.sh

[ $# != 2 ] && echo "Usage: run.sh <config> <mailbox url>" && exit 1
java -cp "$CLASSPATH:$MDBBIN" MailImport $@
[ $? == 0 ] && echo "done" || echo "failed"

