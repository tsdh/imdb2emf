(defproject imdb2emf "0.1.0-SNAPSHOT"
  :description "A Parser from IMDb files to EMF models."
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License"
            :url "http://www.gnu.org/licenses/gpl.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [funnyqt "0.14.1"]]
  :jvm-opts ^:replace ["-Xmx2G"]
  :main imdb2emf.main)
