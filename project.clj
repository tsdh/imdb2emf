(defproject imdb2emf "1.4.0"
  :description "A Parser from IMDb files to EMF models or JGraLab TGraphs."
  :url "https://github.com/tsdh/imdb2emf"
  :license {:name "GNU General Public License"
            :url "http://www.gnu.org/licenses/gpl.html"
            :distribution :repo}
  :dependencies [[funnyqt "0.38.5"]]
  :jvm-opts ^:replace ["-Xmx6G"]
  :global-vars {*warn-on-reflection* true
                *assert* false}
  :main imdb2emf.main
  :aot  [imdb2emf.main imdb2emf.serialize])
