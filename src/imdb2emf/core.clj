(ns imdb2emf.core
  (:require [clojure.java.io   :as io]
            [clojure.string    :as str]
            [funnyqt.utils     :as u]
            [funnyqt.emf       :as emf]
            [funnyqt.generic   :as g])
  (:import (java.util.zip GZIPInputStream)
           (java.io File)))

(def +verbose+ true)

(emf/load-ecore-resource (io/resource "movies.ecore"))

(defn file-line-seq [^String file-name]
  (cond
   (.endsWith file-name ".list.gz")
   (line-seq (io/reader (GZIPInputStream. (io/input-stream file-name))))

   (.endsWith file-name ".list")
   (line-seq (io/reader (io/file file-name)))

   :else (u/errorf "Unknown file type: %s" file-name)))

(defn pick-file [dir name]
  (let [f  (io/file dir (str name ".list"))
        gf (io/file dir (str name ".list.gz"))]
    (cond
     (.exists f)  (.getPath f)
     (.exists gf) (.getPath gf)
     :else (u/errorf "No such file: %s or %s" f gf))))

(def ^:dynamic ^:private *movies-map*)
(def ^:dynamic ^:private *model*)
(def movietype-video     (emf/eenum-literal 'MovieType.VIDEO))
(def movietype-videogame (emf/eenum-literal 'MovieType.VIDEOGAME))
(def movietype-tv        (emf/eenum-literal 'MovieType.TV))
(def movietype-movie     (emf/eenum-literal 'MovieType.MOVIE))

(def movie-rx #"^([^\"\(]+)\s(\(\d\d\d\d(?:/[IVXCM]+)?\))\s(\([A-Z]+\))?\s+(\d\d\d\d)$")
(defn parse-movies [file-name max-movie-count]
  (let [i (atom 0)]
    (doseq [l (file-line-seq file-name)
            :while (or (== -1 max-movie-count)
                       (< @i max-movie-count))
            :let [match (re-matches movie-rx l)]]
      (when match
        (swap! i inc)
        (let [[_ title year-n type year] match
              movie (emf/ecreate! *model* 'Movie
                                  :title title
                                  :year  (Integer/parseInt year)
                                  :type  (case type
                                           "(V)"  movietype-video
                                           "(VG)" movietype-videogame
                                           "(TV)" movietype-tv
                                           nil    movietype-movie))
              movie-id (str/trim (str title " " year-n (when type (str " " type))))]
          (swap! *movies-map* assoc
                 movie-id
                 movie)
          (when +verbose+
            (println (str "Movie: " title " (" year ") " type))))))
    @i))

(def actor-rx #"^([^\t]+)?\t+((?:[^\"\(\t]+)\s(?:\(\d\d\d\d(?:/[IVXCM]+)?\))(?:\s+\([A-Z]+\))?).*")
(defn parse-actors [file-name]
  (let [i (atom 0)
        cls (if (re-matches #".*/actresses\..*" file-name)
              'Actress
              'Actor)
        current-actor  (atom nil)
        current-movies (atom #{})]
    (doseq [l (file-line-seq file-name)
            :let [match (re-matches actor-rx l)]]
      (when match
        (let [[_ actor-name movie-id] match
              movie-id (str/trim movie-id)]
          (when actor-name
            ;; New actor starts, so persist the current one
            (when (and @current-actor (seq @current-movies))
              (swap! i inc)
              (locking *model*
                (emf/ecreate! *model* cls
                              :name   actor-name
                              :movies @current-movies)
                (when +verbose+
                  (println (str cls ": " actor-name " \t=> " (count @current-movies) " Movie(s)")))))
            (reset! current-actor  actor-name)
            (reset! current-movies #{}))
          (when-let [movie (@*movies-map* movie-id)]
            (swap! current-movies conj movie)))))
    @i))

(def ratings-rx #"\s+(?:[0-9.\*]+)\s+(?:\d+)\s+(\d+\.\d)\s+((?:[^\"\(\t]+)\s(?:\(\d\d\d\d(?:/[IVXCM]+)?\))(?:\s+\([A-Z]+\))?)")
(defn parse-ratings [file-name]
  (let [i (atom 0)]
    (doseq [l (drop-while #(not= "MOVIE RATINGS REPORT" %)
                          (file-line-seq file-name))
            :let [match (re-matches ratings-rx l)]]
      (when match
        (let [[_ rating movie-id] match
              movie-id (str/trim movie-id)]
          (when-let [movie (@*movies-map* movie-id)]
            (swap! i inc)
            (locking *model*
              (emf/eset! movie :rating (Double/parseDouble rating))
              (when +verbose+
                (println (str "Rating: " movie-id "\t=> " rating))))))))
    @i))

(defn print-class-counts []
  (let [counts (atom {})]
    (doseq [el (emf/eallobjects *model*)]
      (swap! counts (fn [m]
                      (assoc m (emf/eclass el)
                             (if-let [c (m (emf/eclass el))]
                               (inc c)
                               1)))))
    (println)
    (println "Class counts")
    (println "============")
    (println)
    (doseq [[cls cnt] @counts]
      (println (str "#" (g/qname cls) ": " cnt)))
    (println (reduce + (vals @counts)) "elements in total")
    (println)))

(defn parse-imdb [dir max-movie-count]
  (binding [*model*      (emf/new-resource)
            *movies-map* (atom {})]
    (let [cmovies       (parse-movies (pick-file dir "movies") max-movie-count)
          fut-actors    (future (parse-actors  (pick-file dir "actors")))
          fut-actresses (future (parse-actors  (pick-file dir "actresses")))
          fut-ratings   (future (parse-ratings (pick-file dir "ratings")))
          [cactors cactresses cratings] [@fut-actors @fut-actresses @fut-ratings]]
      ;;(print-class-counts)
      (println)
      (println "Movies with zero actors:")
      (let [i (atom 0)]
        (doseq [[mid m] (filter (fn [[id m]]
                                  (zero? (count (emf/eget m :persons))))
                                @*movies-map*)]
          (swap! i inc)
          (println (str "  - '" mid "'")))
        (println @i "movies with zero actors in total."))
      (println)
      (println "Parsed" cmovies "movies with" cactors "actors,"
               cactresses "actresses, and"
               cratings "ratings.")
      (println (+ cmovies cactors cactresses) "elements in total.")
      (println))
    *model*))
