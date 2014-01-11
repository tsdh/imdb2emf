(ns imdb2emf.main
  (:require [imdb2emf.core      :as i2e]
            [imdb2emf.fast-save :as fs]))

(defn -main [& args]
  (when (or (zero? (count args))
            (> (count args) 2))
    (println "Usage: lein run <imdb-dir>")
    (println "       lein run <imdb-dir> <max-movie-count>")
    (System/exit 1))
  (let [[imdb-dir max-movie-count] args
        max-movie-count (if max-movie-count
                          (Integer/parseInt max-movie-count)
                          -1)]
    (fs/save-movies-model
     (i2e/parse-imdb imdb-dir max-movie-count)
     (if (= -1 max-movie-count)
       "imdb.movies"
       (str "imdb-" max-movie-count ".movies")))))
