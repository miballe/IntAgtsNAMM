#!/bin/bash
#
# Usage
#   sh ./runServer.sh
#

TACAA_HOME=`pwd`
echo $TACAA_HOME
echo $CLASSPATH
java -cp "lib/*" se.sics.tasim.logtool.Main -handler tau.tac.adx.parser.GeneralHandler -file ./../ExecutionLogs/game117.slg.gz  -adnet > AdnetLog117.txt
-bank -ucs -rating -campaign