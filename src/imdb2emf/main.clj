(ns imdb2emf.main
  (:require [imdb2emf.core            :as i2e]
            [imdb2emf.fast-xmi-export :as xmi]
            [imdb2emf.serialize       :as bin]
            [funnyqt.emf              :as emf])
  (:gen-class))

(defn -main [& args]
  (when (or (zero? (count args))
            (> (count args) 2))
    (println "Usage: lein run <imdb-dir>")
    (println "       lein run <imdb-dir> <max-movie-count>")
    (System/exit 1))
  (let [[imdb-dir max-movie-count] args
        max-movie-count (if max-movie-count
                          (Integer/parseInt max-movie-count)
                          -1)
        model (i2e/parse-imdb imdb-dir max-movie-count)
        fname (format "imdb-%s-%s.movies"
                      (if (== -1 max-movie-count)
                        "all"
                        (format "%07d" max-movie-count))
                      (count (emf/eallobjects model)))]
    (xmi/save-movies-model model fname)
    (bin/save-movies-model model (str fname ".bin"))))
