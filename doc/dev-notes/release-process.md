% Release Process
% John Gabriele

Just notes for myself. Nothing to see here. `:)`

 #. update version number in
      * core.clj
      * bin/rippledoc.sh
      * the link to the jar in doc/install.md
 #. git add & commit
 #. `lein uberjar`
 #. cp the standalone jar to ~/bin & u-v/sw/rippledoc
 #. cp bin/rippledoc.sh to ~/bin
 #. `cd doc`, `rippledoc.sh` and cp -r \* docs to u-v/sw/rippledoc
 #. `git push`
 #. rsync docs to u-v
