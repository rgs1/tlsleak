Tracking down a memory leak related to ClientAuth
==============================================================

**Table of Contents**

-  `What <#what>`__
-  `Repro <#repro>`__
-  `Fix <#fix>`__

What
~~~~~

We are hitting a memory leak when using TLS with client certificate
authentication enabled. It's unclear where in the stack the leak is
being triggered. Read below for workarounds and/or possible fixes.

Repro
~~~~~

You'll need a KeyStore and a TrustStore:

::

    $ mvn clean package -am -DskipTests
    $ cd target
    $ mkdir tlsleak && tar -xf tlsleak-1.0-SNAPSHOT-bin.tar.gz -C tlsleak && cd tlsleak
    $ bash -x scripts/run-server.sh  <keystore> <truststore> <password> &
    $ bash -x scripts/run-client.sh  <keystore> <truststore> <password> &
    ...

And then watch the client's RSS grow unbounded from another terminal:

::

    $ watch 'ps aux | grep java | grep -v grep'

Fix
~~~

We learned that disabling client auth makes the leak go away. For this, we
used the `honest profiler <https://github.com/jvm-profiling-tools/honest-profiler>`__
to pinpoint the different code paths. The flamegraphs suggested that we
look at the cert_requested callback path:

.. image:: https://github.com/rgs1/tlsleak/blob/master/flamegraphs/flamegraph-leak.png

We then removed what looks to be an extra reference taken from the cert_requested callback:

https://github.com/rgs1/netty-tcnative/commit/00d20713924f34ac683e96cb2f5ef868593ed61f

However, it's possible that that reference up call is valid but somewhere in finagle
the cert ref isn't decremented. Or, it could be due to the fact that we are mixing
an old version of Finagle — which pulls in Netty 3 — with Netty 4.
