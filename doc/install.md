% Install
% John Gabriele

Prerequisites
=============

[Rippledoc](https://github.com/uvtc/rippledoc) is a
[Clojure](http://clojure.org/) program which only requires that you
have the JVM installed. On Debian-based distros, install Java like so:

    apt-get install openjdk-7-jre

Rippledoc is driven by a tiny shell script (`rippledoc.sh`) which
merely has `java` run the rippledoc jar file.

You'll also need
[Pandoc](http://johnmacfarlane.net/pandoc/). Instructions for
installing Pandoc are on its website.


Download and Install
====================

To install Rippledoc, just download and save the latest [Rippledoc jar
file](http://www.unexpected-vortices.com/sw/rippledoc/rippledoc-0.1.1-standalone.jar)
and
[rippledoc.sh](https://raw.githubusercontent.com/uvtc/rippledoc/master/bin/rippledoc.sh)
to somewhere in your $PATH (such as `~/bin` or `/usr/local/bin`) and
make sure rippledoc.sh is executable (`chmod +x rippledoc.sh`).
