% Release Process
% John Gabriele

 #. update version number in
      * core.clj
      * bin/rippledoc.sh
      * the link to the jar in doc/install.md
 #. git add & commit
 #. `lein uberjar` and cp the jar to ~/bin & u-v/sw/rippledoc
 #. `cd doc`, `rippledoc.sh` and cp -r \* docs to u-v/sw/rippledoc
 #. `git push`
 #. rsync docs to u-v
