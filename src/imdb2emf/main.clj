(ns imdb2emf.main
  (:require [clojure.string :as str]
            [imdb2emf.core            :as i2e]
            [imdb2emf.fast-xmi-export :as xmi]
            [imdb2emf.serialize       :as bin]
            [funnyqt.emf              :as emf]
            [funnyqt.tg               :as tg])
  (:gen-class))

(defn elem-count [m]
  (if (tg/graph? m)
    (tg/vcount m)
    (count (emf/eallobjects m))))

(defn save-model [m fname]
  (if (tg/graph? m)
    (tg/save-graph m (str fname ".tg.gz"))
    (do
      (xmi/save-movies-model m fname)
      (bin/save-movies-model m (str fname ".bin")))))

(defn -main [& args]
  (when (or (zero? (count args))
            (> (count args) 3))
    (println "Usage: lein run <kind> <imdb-dir>")
    (println "       lein run <kind> <imdb-dir> <max-movie-count>")
    (println "<kind> should either be EMF to create EMF models or TG to create TGraphs.")
    (println "<imdb-dir> is the directory containing the IMDb files.")
    (println "<max-movie-count> is the maximum number of movies to be parsed.")
    (System/exit 1))
  (let [[kind imdb-dir max-movie-count] args
        max-movie-count (if max-movie-count
                          (Integer/parseInt max-movie-count)
                          -1)
        model (i2e/parse-imdb (keyword (str/lower-case kind)) imdb-dir max-movie-count)
        fname (format "imdb-%s-%s.movies"
                      (if (== -1 max-movie-count)
                        "all"
                        (format "%07d" max-movie-count))
                      (elem-count model))]
    (save-model model fname)))
