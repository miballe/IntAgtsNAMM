#!/bin/bash
#
# Usage
#   sh ./runServer.sh
#

set TACAA_HOME="pwd"
echo %TACAA_HOME%
echo $CLASSPATH

java -cp "lib/*" se.sics.tasim.sim.Main
