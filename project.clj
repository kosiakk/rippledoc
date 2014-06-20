(defproject rippledoc "0.1.0"
  :description "A particularly easy-to-use doc processing tool."
  :url "https://github.com/uvtc/rippledoc"
  :license {:name "GNU General Public License v3"
            :url "https://www.gnu.org/licenses/gpl.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [me.raynes/fs        "1.4.6"]
                 [hiccup              "1.0.5"]]
  :main ^:skip-aot rippledoc.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
