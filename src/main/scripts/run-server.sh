#!/bin/bash

export PARENT_DIR=.
export JAVA_MAIN=${JAVA_MAIN:=com.pinterest.tlsleak.TlsLeakServer}
export CP=${PARENT_DIR}:${PARENT_DIR}/*:${PARENT_DIR}/lib/*
export HEAP_SIZE=${HEAP_SIZE:=512m}
export NEW_SIZE=${NEW_SIZE:=256m}

exec java -server -Xmx${HEAP_SIZE} -Xms${HEAP_SIZE} -XX:NewSize=${NEW_SIZE} -XX:MaxNewSize=${NEW_SIZE} \
-XX:+PerfDisableSharedMem \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:InitiatingHeapOccupancyPercent=45 \
-Dnetworkaddress.cache.ttl=60 -Djava.net.preferIPv4Stack=true \
-cp ${CP} \
-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=10102 \
-Dfile.encoding=UTF-8 \
${JAVA_MAIN} $@