% TODO
% John Gabriele

  * check to make sure there's at least one doc file present other
    than index.md.

  * Title in browser window & bookmark should include project name

  * index.md is special and should not be processed with --toc and -N

  * Add options:

        --help
        --clean      # remove all generated .html, toc.conf, toc.md, and styles files
        --modified   # shows which md files have been modified and need processing
        --readme-is-index {title}   # for when index.md file is not present,
                                    # but ../README.md is.

  * If you pass one arg --- an md filename --- just process that one file
    and exit.

  * maybe also create one big all-one-page.{md,html} (?)

  * printable version shows author name...

  * do more testing, ex.:

      * change ordering in toc (mix up ordering)
