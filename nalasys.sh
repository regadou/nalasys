#!/bin/sh

debugargs="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
debug=""
prog=`readlink -f $0`
dir=`dirname $prog`
cd "$dir"
webroot=$dir/web
LIB=-Dsun.boot.library.path=$webroot/WEB-INF/lib/native
CP=$webroot/WEB-INF/classes
for i in $webroot/WEB-INF/lib/*; do
	CP=$CP:$i
done
for i in $dir/lib/*; do
	CP=$CP:$i
done

for  i in $@; do
   if [ "$i" == "debug" ]; then
      debug=$debugargs
   fi
done

java -cp $CP $LIB $debug org.regadou.nalasys.Main $@

