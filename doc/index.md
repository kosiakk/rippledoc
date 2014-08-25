% Rippledoc
% John Gabriele
% 2014-06

**A particularly easy-to-use doc processing tool.**

Rippledoc is a command-line program which generates easily-navigable
html from a bunch of Markdown-formatted text files. That is, it
basically turns this:

~~~
doc/
    foo.md
    bar.md
    moo/
        baz.md
~~~

into something like:

~~~
doc/
    foo.md
    foo.html
    bar.md
    bar.html
    moo/
        baz.md
        baz.html
~~~

Under the hood it uses [Pandoc](http://johnmacfarlane.net/pandoc/) to
do the markdown ➞ html conversion.

Rippledoc requires zero configuration. You just run it in a directory
containing Markdown-formatted text files (see [more
info](more-info.html) for a few rules you've got to follow) and it
does the rest.

You can find the source located at
<https://github.com/uvtc/rippledoc>.


OS Compatibility
================

The author has not given even a passing thought to running this
program on any OS other than GNU/Linux.



Quick Usage
===========

~~~bash
cd ~/my-project/doc
# Rippledoc needs *at least* an index.md file present.
touch _copyright index.md tutorial.md  # news.md, ... others?
# edit those files
rippledoc.sh
~~~

and point your browser to ~/my-project/doc/index.html to see the
results.

> Incidentally, [this site you're reading
> now](http://www.unexpected-vortices.com/sw/rippledoc/index.html) was
> generated using Rippledoc.

To upload your docs to a server, you might use rsync:

~~~bash
rsync -urv --delete /path/to/your-proj/doc you@remote:public_html/your-proj
~~~

That will put the local `doc` directory into the remote `your-proj`
directory.



License
=======

Copyright 2014 John Gabriele

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
