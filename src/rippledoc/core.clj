(ns rippledoc.core
  (:require [clojure.string     :as str]
            [clojure.java.io    :as io]
            [clojure.java.shell :as sh]
            [clojure.set        :as set]
            [me.raynes.fs       :as fs]
            [hiccup.core        :as hic-c])
  (:gen-class))

;; Copyright 2014 John Gabriele <jgabriele@fastmail.fm>
;;
;; This file is part of Rippledoc.
;;
;; Rippledoc is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published
;; by the Free Software Foundation, either version 3 of the License,
;; or (at your option) any later version.
;;
;; Rippledoc is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with Rippledoc.  If not, see <http://www.gnu.org/licenses/>."

(def version "0.1.0")

(def proj-title     (atom ""))
(def copyright-info (atom ""))

;; Some data structures we'll populate early-on to facilitate
;; generating toc.md files later.

;; A set of dirs with no .md files in or under them at all.  These
;; don't need to have a toc.conf or toc.md, don't need to appear in
;; any ToC, etc.
(def dirs-to-skip (atom #{}))

;; path/html-filename --> doc title
(def doc-title-for (atom {}))

;; path/dirname --> seq of doc or dir names (from toc.conf)
(def ordered-nav-paths-for (atom {}))

;; Used for figuring out "prev" and "next" links.
(def full-ordered-list-of-pages (atom []))

(def tmp-dir (System/getProperty "java.io.tmpdir"))

(def _html-before "
<div id=\"main-outer-box\">

<div id=\"my-header\">
  <a href=\"{{path-to-index}}\">{{project-name}}</a>
</div>

<div id=\"trunk-box\">

<div id=\"nav-pane\">
<p>{{path}}</p>
{{nav-pane-links}}
</div>

<div id=\"center-box\">

<div id=\"header-nav-bar\">
{{nav-bar-content}}
</div>

<div id=\"content\">
")

(def _html-after "
</div> <!-- content -->

<div id=\"footer-nav-bar\">
{{nav-bar-content}}
</div>

</div> <!-- center-box -->
</div> <!-- trunk-box -->

<div id=\"my-footer\">
{{copyright-info}}<br/>
<a href=\"{{link-to-this-page-md}}\">Pandoc-Markdown source for this page</a><br/>
(Docs processed by
<a href=\"http://www.unexpected-vortices.com/sw/rippledoc/index.html\">Rippledoc</a>.)
</div> <!-- my-footer -->

</div> <!-- main-outer-box -->
")

(declare process-dir-create-toc-conf-files
         process-dir-create-toc-md-files
         process-dir-md-files)

(declare populate-doc-titles-ds    ; "ds" = "data structure"
         populate-nav-paths-ds
         populate-dirs-to-skip-ds
         populate-full-ordered-list-of-pages-ds)

(declare index-md-file-here?
         styles-files-here?
         set-copyright-info
         doc-title-of
         pandoc-process-md-file)

;;==================================================================
(defn -main
  [& args]
  (println "==================== Rippledoc, version" version "====================")
  (index-md-file-here?)
  (reset! proj-title (doc-title-of "index.md"))
  (println (str "Generating docs for \"" @proj-title "\"..."))

  (styles-files-here?)

  (if (fs/exists? "_copyright")
    (do
      (println "Found _copyright file and using its contents...")
      (reset! copyright-info (slurp "_copyright")))
    (do
      (println "[**] Unable to find \"_copyright\" file here. Please make sure
[**] you have one in the top-level of your doc directory, and
[**] that you're running this program from that directory.
[**] Exiting.")
      (System/exit 0)))

  (println "Noting any dirs to skip (i.e., those with no .md files in or below them)...")
  (populate-dirs-to-skip-ds ".")
  (when (seq @dirs-to-skip)
    (doseq [d (sort @dirs-to-skip)]
      (println "  Skipping" d)))

  (println "Recording each md file's doc title...")
  (populate-doc-titles-ds)

  (println "Fashioning toc.conf's as needed...")
  (process-dir-create-toc-conf-files ".")

  (println "Reading in all toc.conf data...")
  (populate-nav-paths-ds ".")
  (populate-full-ordered-list-of-pages-ds ".")

  (println "Whipping up toc.md files...")
  (process-dir-create-toc-md-files ".")

  (println "Transmogrifying .md files into html files...")
  (process-dir-md-files ".")  ; Process all .md files with pandoc.

  (newline)
  (println "Done.")
  (shutdown-agents))  ; req'd to avoid hanging here from sh/sh.


;;==================================================================
(defn index-md-file-here?
  []
  (if-not (fs/exists? "index.md")
    (do (println "
[**] Couldn't find an index.md file here.
[**] Are you at the top of your docs directory?
[**] If so, create an index.md and try again.")
        (System/exit 0))))


(declare create-styles-file)


(defn styles-files-here?
  []
  (doseq [styles-filename ["styles.css" "styles-printable.css"]]
    (when-not (fs/exists? styles-filename)
      (println "Didn't find a" styles-filename "file here. Creating one...")
      (spit styles-filename
            (slurp (io/resource styles-filename))))))


(defn doc-title-of
  [filename]
  (let [first-line (first (str/split-lines (slurp filename)))]
    (if (= (re-find #"^% " first-line) "% ")
      (str/trim (subs first-line 2))
      (do (println "
[**] The first line of" filename "should look something like
[**] this: \"% The Title\" (a percent sign, space, and the title).
[**] That is, it should constitute a Pandoc title block.
[**] Please fix" filename "and try again. Exiting.")
          (System/exit 0)))))


(defn check-name
  "Just checks to see that there's no spaces or other unsavory
characters in the filename or dirname."
  [name]
  (when (not= name ".")  ; Because fs/base-name is weird about ".".
    (let [bn (fs/base-name name)]
      (if-not (re-matches #"[\w.-]+" bn)
        (do (println "
[**] Please use file and directory names consisting of only letters,
[**] numbers, dashes, underscores, and dots. Rippledoc does not like
[**] the looks of" (str "\"" bn "\"") "in
[**]" (str "\"" name "\"."))
            (println "[**] Bailing out.")
            (System/exit 0))))))


(defn get-all-md-filenames
  "Returns a list like `[\"./foo.txt\" \"./bar/baz.md\" ...]` of
all md files here and below. Checks them with `check-name` as it
goes. Used for creating the filename -> doc-title map."
  []
  (let [md-filenames (map #(.getPath %)
                          (filter #(.isFile %)
                                  (file-seq (io/file "."))))
        md-filenames (filter #(re-find #"\.md$" %)
                             md-filenames)
        _            (doseq [md-fnm md-filenames]
                       (check-name md-fnm))]
    md-filenames))


(defn populate-doc-titles-ds
  []
  (let [md-filenames   (get-all-md-filenames)
        doc-titles-map (reduce (fn [accum fnm]
                                 (assoc accum
                                   fnm
                                   (doc-title-of fnm)))
                               {}
                               md-filenames)]
    (reset! doc-title-for doc-titles-map)))


(declare list-dirnames-at
         list-md-filenames-at
         create-default-toc-conf-file-at
         check-toc-conf-file-at
         create-toc-md-at
         any-mds-here-or-below?)


(defn populate-nav-paths-ds
  [path]
  (when-not (@dirs-to-skip path)
    (let [targets  (map (partial str path "/")
                        (str/split-lines
                         (str/trim
                          (slurp (str path "/toc.conf")))))
          dirnames (list-dirnames-at path)]
      (swap! ordered-nav-paths-for conj [path targets])
      (doseq [d dirnames]
        (populate-nav-paths-ds d)))))


(defn populate-dirs-to-skip-ds
  "If a given directory (and any subdirectories) contains no .md files,
then rippledoc can skip over it entirely (it won't show up in ToC's
either)."
  [path]
  (if (any-mds-here-or-below? path)
    (let [dirnames (list-dirnames-at path)]
      (doseq [d dirnames]
        (populate-dirs-to-skip-ds d)))
    (swap! dirs-to-skip conj path)))


(defn any-mds-here-or-below?
  "Expects `path` to be a dirname. Checks for any .md files in `path`
or below."
  [path]
  (or (seq (filter #(re-find #"\.md$" %)
                   (map fs/base-name
                        (fs/list-dir path))))
      (some boolean (let [dirnames (list-dirnames-at path)]
                      (for [d dirnames]
                        (any-mds-here-or-below? d))))))


(defn populate-full-ordered-list-of-pages-ds
  "Gets all necessary info from `ordered-nav-paths-for`."
  [path]
  (doseq [x (get @ordered-nav-paths-for path)]
    (if (re-find #"\.md$" x)
      (swap! full-ordered-list-of-pages conj x)
      (populate-full-ordered-list-of-pages-ds x))))


(defn process-dir-create-toc-conf-files
  "Starting at `path` and descending into all subdirs, check for
(and create if necessary) toc.conf files."
  [path]
  (when-not (@dirs-to-skip path)
    (when-not (fs/exists? (str path "/toc.conf"))
      (println (str "  Creating " path "/toc.conf..."))
      (create-default-toc-conf-file-at path))
    (check-toc-conf-file-at path)
    (let [dirnames (list-dirnames-at path)]
      (doseq [d dirnames]
        (process-dir-create-toc-conf-files d)))))


(defn process-dir-create-toc-md-files
  "Creates toc.md files; first in `path`, then in subdirs."
  [path]
  (when-not (@dirs-to-skip path)
    (create-toc-md-at path) ; We'll just go ahead and recreate them every time.
    (let [dirnames (list-dirnames-at path)]
      (doseq [d dirnames]
        (process-dir-create-toc-md-files d)))))


(defn process-dir-md-files
  [path]
  ;; `path` is always a dir name which always starts with a dot,
  ;; and which does not end in a slash.
  (let [md-filenames (list-md-filenames-at path)
        dirnames     (list-dirnames-at     path)]
    ;; Those dirnames and md-filenames are full relative paths
    ;; to the dirs and md-files in `path`.
    (doseq [md-filename md-filenames]
      ;;(println "processing" md-filename "...")
      (pandoc-process-md-file md-filename))
    (doseq [d dirnames]
      (when-not (@dirs-to-skip d)
        (process-dir-md-files d)))))


(defn create-default-toc-conf-file-at
  [path]
  (let [md-filenames (map fs/base-name (list-md-filenames-at path))
        md-filenames (remove (fn [fnm]
                               (= fnm "toc.md"))
                             md-filenames)
        md-filenames (if (= path ".")
                       (remove (fn [fnm]
                                 (= fnm "index.md"))
                               md-filenames)
                       md-filenames)
        dirnames     (map fs/base-name
                          (remove @dirs-to-skip
                                  (list-dirnames-at path)))]
    (spit (str path "/toc.conf")
          (str (if (seq md-filenames)
                 (str (str/join "\n" md-filenames)
                      "\n"))
               (if (seq dirnames)
                 (str (str/join "\n" dirnames)
                      "\n"))))))


(defn check-toc-conf-file-at
  "Make sure that the toc.conf file in `path` looks copacetic."
  [path]
  (let [files-dirs-in-toc (sort (map (partial str path "/")
                                     (str/split-lines
                                      (str/trim
                                       (slurp (str path "/toc.conf"))))))
        files-dirs-here   (sort (concat (remove (fn [fnm] (re-find #"toc\.md$" fnm))
                                                (list-md-filenames-at path))
                                        (remove @dirs-to-skip (list-dirnames-at path))))
        files-dirs-here   (if (= path ".")
                            (remove #(= % "./index.md") files-dirs-here)
                            files-dirs-here)]
    (when-not (= files-dirs-in-toc
                 files-dirs-here)
      (println "[**] toc.conf in" path "doesn't agree with what's actually there.")
      (print "[**] In toc.conf:\n  ")
      (prn files-dirs-in-toc)
      (print "[**] Actually there:\n  ")
      (prn files-dirs-here)
      (println "[**] Exiting.")
      (System/exit 0))))


(declare create-toc-md-ds-for)


(defn find-depth-of-filename
  "Pass in a filename, e.g., \"./foo.md\", or \"./bar/baz/moo.md\"."
  [fnm-path]
  (dec (count (re-seq #"/" fnm-path))))


(defn find-depth-of-path
  "Pass in a dirname, e.g., \".\", or \"./foo\"."
  [path]
  (count (re-seq #"/" path)))


(defn create-toc-md-at
  "Create a toc.md file in `path` consisting of everything in `path`
and below (in subdirs)."
  [path]
  (spit (str path "/toc.md")
        (str (if (= path ".")
               "% Table of Contents\n\n"
               (str "% Contents of /" (subs path 2) "\n\n"))
             (hic-c/html (create-toc-md-ds-for path
                                               (find-depth-of-path path)))
             "\n")))

;; To create prev/next and toc links to other docs, we need to keep
;; track of the path we start at --- the path where the current doc
;; resides, such that when we construct paths for links to docs/dirs
;; further down, we know how much to trim off the front.

(declare chop-n-leading-dirs)


(defn create-toc-md-ds-for
  "Creates a data structure for a given ToC, to be used by hiccup.
`depth` stays the same for each recursive call, of course, because
it's the level where the particular toc.md lives which is currently
being generated."
  [path depth]
  [:ul (for [item (@ordered-nav-paths-for path)]
         [:li (if (fs/file? item)
                [:a {:href (chop-n-leading-dirs (str/replace item #"\.md$" ".html")
                                                depth)}
                 (doc-title-of item)]
                (list [:a {:href (str (chop-n-leading-dirs item depth)
                                      "/toc.html")}
                       (str (fs/base-name item) "/")]
                      (create-toc-md-ds-for item depth)))])])


(defn chop-n-leading-dirs
  "Given a pathname `path` and an integer `n`, chop off `n` path segments
(`n` forward-slashes-worth from the left)."
  [path n]
  ;;    Gets you something like `([1 \/] [5 \/] [9 \/])`.
  (let [indices-of-slashes (filter (fn [p]
                                     (= (p 1) \/))
                                   (map-indexed vector path))
        index              ((nth indices-of-slashes n) 0)]
    (subs path (inc index))))


(declare str-times)


(defn list-dirnames-at
  "Returns dirnames with full paths (that is, starting with `path`)."
  [path]
  ;; As of 1.4.6, fs/list-dir returns file objects. But we want
  ;; relative paths to them, not absolute, which is why we strip
  ;; of path info and then glue it back on.
  (map (partial str path "/")
       (map fs/base-name
            (filter fs/directory? (fs/list-dir path)))))


(defn list-md-filenames-at
  [path]
  ;; re. fs/list-dir, see comment in `list-dirnames-at`, above.
  (filter #(and (fs/file? %)
                (re-find #"\.md$" %))
          (map (partial str path "/")
               (map fs/base-name
                    (fs/list-dir path)))))


(defn make-breadcrumbs
  "Of course, assumes you're passing in something like
\"./foo/bar/baz.html\"."
  [path]
  (let [r-pieces (reverse (rest (str/split path #"/")))
        filename (first r-pieces)
        r-dirs   (rest  r-pieces)
        up-n-up  (iterate #(str % "../")
                          "../")
        paths    (map (fn [up piece]
                        (str up piece "/toc.html"))
                      up-n-up
                      r-dirs)
        paths    (reverse paths)  ; back to the usual order
        dirs     (reverse r-dirs)]
    (if (empty? dirs)
      (str "/" filename)
      (str "/"
           (str/join "/" (map (fn [dirpath dirname]
                                (str "<a href=\"" dirpath "\">" dirname "</a>"))
                              paths
                              dirs))
           "/"
           filename))))


(defn find-the-one-before
  "For creating the \"prev\" link. Assumes you will never pass
it the first md-fnm in the full ordered list of pages."
  [md-fnm]
  (let [idx (.indexOf @full-ordered-list-of-pages md-fnm)]
    (assert (not= idx 0))
    (get @full-ordered-list-of-pages (dec idx))))


(defn find-the-one-after
  "For creating the \"next\" link. Assumes you will never pass
it the last md-fnm in the full ordered list of pages."
  [md-fnm]
  (let [idx (.indexOf @full-ordered-list-of-pages md-fnm)]
    (assert (not= idx (dec (count @full-ordered-list-of-pages))))
    (get @full-ordered-list-of-pages (inc idx))))


;; Shield your eyes!
(defn create-before-and-after-html-frag-files
  [md-fnm]
  (let [ht-fnm      (str/replace md-fnm #"\.md$" ".html")
        depth       (find-depth-of-filename md-fnm)
        dirname     (if (= depth 0)
                      "."
                      (str/replace md-fnm #"/[\w.-]+\.md$" ""))
        nav-bar-content (hic-c/html (if (or (re-find #"/toc\.md$" md-fnm)
                                            (= md-fnm "./index.md")
                                            (= md-fnm (first @full-ordered-list-of-pages)))
                                      "← prev"
                                      [:a {:href (str (str-times "../" depth)
                                                      (str/replace (find-the-one-before md-fnm)
                                                                   #"\.md$"
                                                                   ".html"))}
                                       "← prev"])
                                    " | "
                                    (cond (or (re-find #"/toc\.md" md-fnm)
                                              (= md-fnm (last @full-ordered-list-of-pages)))
                                          "next →"
                                          (= md-fnm "./index.md")
                                          [:a {:href (str/replace
                                                      (first @full-ordered-list-of-pages)
                                                      #"\.md$"
                                                      ".html")}
                                           "next →"]
                                          :else
                                          [:a {:href (str (str-times "../" depth)
                                                          (str/replace (find-the-one-after md-fnm)
                                                                       #"\.md$"
                                                                       ".html"))}
                                           "next →"])
                                    " &nbsp; &nbsp; "
                                    [:a {:href (str (str-times "../" depth)
                                                    "toc.html")}
                                     "Top-level ToC"]
                                    " &nbsp; &nbsp; <b>"
                                    (make-breadcrumbs ht-fnm)
                                    "</b> &nbsp; &nbsp; ("
                                    [:a {:href (chop-n-leading-dirs
                                                (str/replace ht-fnm
                                                             #"\.html$"
                                                             "-printable.html")
                                                depth)}
                                     "printable version"]
                                    ")<br/>\n")
        before-frag _html-before
        before-frag (str/replace before-frag
                                 #"\{\{path-to-index\}\}"
                                 (str (str-times "../" depth) "index.html"))
        before-frag (str/replace before-frag
                                 #"\{\{project-name\}\}"
                                 @proj-title)
        before-frag (str/replace before-frag
                                 #"\{\{path\}\}"
                                 (if (= dirname ".")
                                   "Top-level docs &amp; dirs:"
                                   (str "In /" (subs dirname 2) ":")))
        before-frag (str/replace before-frag
                                 #"\{\{nav-pane-links\}\}"
                                 (hic-c/html [:ul
                                              (for [path (@ordered-nav-paths-for dirname)]
                                                (let [bname (fs/base-name path)]
                                                  (if (fs/directory? path)
                                                    [:li [:a
                                                          {:href (str bname "/toc.html")}
                                                          (str bname "/")]]
                                                    [:li (if (= path md-fnm)
                                                           [:b (@doc-title-for path)]
                                                           [:a
                                                            {:href (str/replace bname
                                                                                #"\.md$"
                                                                                ".html")}
                                                            (@doc-title-for path)])])))]))
        before-frag (str/replace before-frag
                                 #"\{\{nav-bar-content\}\}"
                                 nav-bar-content)
        ;; ---
        after-frag _html-after
        after-frag (str/replace after-frag
                                #"\{\{nav-bar-content\}\}"
                                nav-bar-content)
        after-frag (str/replace after-frag
                                #"\{\{copyright-info\}\}"
                                @copyright-info)
        after-frag (str/replace after-frag
                                #"\{\{link-to-this-page-md\}\}"
                                (fs/base-name md-fnm))]
    (spit (str tmp-dir "/before.html")
          before-frag)
    (spit (str tmp-dir "/after.html")
          after-frag)))


(defn pandoc-process-md-file
  "Process just one md file."
  [md-fnm]
  (let [html-fnm    (str/replace md-fnm   #"\.md$"   ".html")
        html-pr-fnm (str/replace html-fnm #"\.html$" "-printable.html")
        _           (print ".")
        depth       (find-depth-of-filename md-fnm)
        _           (create-before-and-after-html-frag-files md-fnm)
        results     (sh/sh "pandoc" "-s" "-S" "--toc" "-N" "--mathjax"
                           (str "--css="
                                (str-times "../" depth)
                                "styles.css")
                           "-B" (str tmp-dir "/before.html")
                           "-A" (str tmp-dir "/after.html")
                           "-o" html-fnm md-fnm)
        results2    (sh/sh "pandoc" "-s" "-S" "--toc" "-N" "--mathjax"
                           (str "--css="
                                (str-times "../" depth)
                                "styles-printable.css")
                           "-o" html-pr-fnm md-fnm)]
    (when (or (not= 0 (results  :exit))
              (not= 0 (results2 :exit)))
      (println "[**] Problem running pandoc on" md-fnm ". Pandoc error(s):")
      (println "[**] for regular file:  " (results :err))
      (println "[**] for printable file:" (results2 :err))
      (println "[**] Bailing out.")
      (System/exit 0))))


(defn str-times
  [st n]
  (str/join (repeat n st)))
