(defproject imdb2emf "0.1.0"
  :description "A Parser from IMDb files to EMF models."
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License"
            :url "http://www.gnu.org/licenses/gpl.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [funnyqt "0.14.4"]]
  :jvm-opts ^:replace ["-Xmx2G"]
  :global-vars {*warn-on-reflection* true
                *assert* false}
  :main imdb2emf.main
  :aot  [imdb2emf.main imdb2emf.serialize])
