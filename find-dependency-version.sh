#!/bin/sh

# $1 project root directory
# $2 dependency name

( cd "$1" && mvn -ntp dependency:tree -Dincludes="org.rascalmpl:$2" | grep "org.rascalmpl:$2:" | cut -d ':' -f 4 )
