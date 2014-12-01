(ns imdb2emf.core
  (:require [clojure.java.io   :as io]
            [clojure.string    :as str]
            [funnyqt.utils     :as u]
            [funnyqt.emf       :as emf]
            [funnyqt.tg        :as tg]
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

(def ^:dynamic ^:private *movie-map*)
(def ^:dynamic ^:private *genre-map*)
(def ^:dynamic ^:private *model*)

(def movietype-movie      (emf/eenum-literal 'MovieType.MOVIE))
(def movietype-tv         (emf/eenum-literal 'MovieType.TV))
(def movietype-video      (emf/eenum-literal 'MovieType.VIDEO))
(def movietype-videogame  (emf/eenum-literal 'MovieType.VIDEOGAME))

(def movie-rx #"^([^\"\(]+)\s(\(\d\d\d\d(?:/[IVXCM]+)?\))\s(\([A-Z]+\))?\s+(\d\d\d\d)$")
(defn parse-movies [file-name max-movie-count]
  (let [i (volatile! 0)]
    (doseq [l (file-line-seq file-name)
            :while (or (== -1 max-movie-count)
                       (< @i max-movie-count))
            :let [match (re-matches movie-rx l)]
            :when match]
      (vswap! i inc)
      (let [[_ title year-n type year] match
            movie (g/create-element!
                   *model* 'Movie
                   {:title title
                    :year  (Integer/parseInt year)
                    :type  (case type
                             "(V)"  (g/enum-constant *model* 'MovieType.VIDEO)
                             "(VG)" (g/enum-constant *model* 'MovieType.VIDEOGAME)
                             "(TV)" (g/enum-constant *model* 'MovieType.TV)
                             nil    (g/enum-constant *model* 'MovieType.MOVIE))})
            movie-id (str/trim (str title " " year-n (when type (str " " type))))]
        (swap! *movie-map* assoc movie-id movie)
        (when +verbose+
          (println (str "Movie: " title " (" year ") " type)))))
    @i))

(def person-rx #"^([^\t]+)?\t+((?:[^\"\(\t]+)\s(?:\(\d\d\d\d(?:/[IVXCM]+)?\))(?:\s+\([A-Z]+\))?).*")
(defn parse-persons [file-name]
  (let [i (volatile! 0)
        cls (cond
             (re-matches #".*/actresses\..*" file-name) 'Actress
             (re-matches #".*/actors\..*"    file-name) 'Actor
             (re-matches #".*/directors\..*" file-name) 'Director)
        current-name   (volatile! nil)
        current-movies (volatile! #{})]
    (doseq [l (file-line-seq file-name)
            :let [match (re-matches person-rx l)]
            :when match]
      (let [[_ person-name movie-id] match
            movie-id (str/trim movie-id)]
        (when person-name
          ;; New person starts, so persist the current one
          (when (and @current-name (seq @current-movies))
            (vswap! i inc)
            (locking *model*
              (g/create-element! *model* cls
                                 {:name   @current-name
                                  :movies @current-movies})
              (when +verbose+
                (println (str cls ": " @current-name " \t=> " (count @current-movies) " Movie(s)")))))
          (vreset! current-name  person-name)
          (vreset! current-movies #{}))
        (when-let [movie (@*movie-map* movie-id)]
          (vswap! current-movies conj movie))))
    @i))

(def ratings-rx #"\s+(?:[0-9.\*]+)\s+(?:\d+)\s+(\d+\.\d)\s+((?:[^\"\(\t]+)\s(?:\(\d\d\d\d(?:/[IVXCM]+)?\))(?:\s+\([A-Z]+\))?)")
(defn parse-ratings [file-name]
  (let [i (volatile! 0)]
    (doseq [l (drop-while #(not= "MOVIE RATINGS REPORT" %)
                          (file-line-seq file-name))
            :let [match (re-matches ratings-rx l)]
            :when match]
      (let [[_ rating movie-id] match
            movie-id (str/trim movie-id)]
        (when-let [movie (@*movie-map* movie-id)]
          (vswap! i inc)
          (locking *model*
            (g/set-aval! movie :rating (Double/parseDouble rating))
            (when +verbose+
              (println (str "Rating: " movie-id "\t=> " rating)))))))
    @i))

(def genre-rx #"^([^\t]+)\t+([-\w]+)$")
(defn parse-genres [file-name]
  (let [i (volatile! 0)
        get-genre (fn [genre-name]
                    (or (@*genre-map* genre-name)
                        (let [g (g/create-element! *model* 'Genre {:name genre-name})]
                          (swap! *genre-map* assoc genre-name g)
                          g)))]
    (doseq [l (drop-while  #(not= "8: THE GENRES LIST" %)
                           (file-line-seq file-name))
            :let [match (re-matches genre-rx l)]
            :when match]
      (let [[_ movie-id genre-name] match
            movie-id (str/trim movie-id)]
        (if-let [movie (@*movie-map* movie-id)]
          (do
            (vswap! i inc)
            (locking *model*
              (g/set-adj! movie :genre (get-genre genre-name))
              (when +verbose+
                (println (str "Genre: " movie-id "\t=> " genre-name)))))
          (println (str "No such movie: <<<" movie-id ">>>")))))
    @i))

(defn parse-imdb [kind dir max-movie-count]
  (binding [*model*      (case kind
                           :tg (tg/new-graph (tg/load-schema (io/resource "movies.tg")))
                           :emf (emf/new-resource)
                           (u/errorf "kind must be :tg or :emf but was %." kind))
            *movie-map* (atom {})
            *genre-map* (atom {})]
    (let [cmovies       (parse-movies (pick-file dir "movies") max-movie-count)
          fut-actors    (future (parse-persons (pick-file dir "actors")))
          fut-actresses (future (parse-persons (pick-file dir "actresses")))
          fut-directors (future (parse-persons (pick-file dir "directors")))
          fut-ratings   (future (parse-ratings (pick-file dir "ratings")))
          fut-genres    (future (parse-genres (pick-file dir "genres")))
          [cactors cactresses cdirectors cratings cgenres]
          [@fut-actors @fut-actresses @fut-directors @fut-ratings @fut-genres]]
      (println)
      (println "Parsed" cmovies "movies with" cactors "actors,"
               cactresses "actresses," cdirectors "directors,"
               cratings "ratings, and" cgenres "genre classifications.")
      (println (+ cmovies cactors cactresses cdirectors (count @*genre-map*)) "elements in total.")
      (println))
    *model*))
