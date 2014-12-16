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
(def +print-non-matching+ false)

(emf/load-ecore-resource (io/resource "movies.ecore"))

(defn file-line-seq [^String file-name]
  (cond
   (.endsWith file-name ".list.gz")
   (line-seq (io/reader (GZIPInputStream. (io/input-stream file-name))
                        :encoding "ISO-8859-15"))

   (.endsWith file-name ".list")
   (line-seq (io/reader (io/file file-name)
                        :encoding "ISO-8859-15"))

   :else (u/errorf "Unknown file type: %s" file-name)))

(defn pick-file [dir name]
  (let [f  (io/file dir (str name ".list"))
        gf (io/file dir (str name ".list.gz"))]
    (cond
     (.exists f)  (.getPath f)
     (.exists gf) (.getPath gf)
     :else (u/errorf "No such file: %s or %s" f gf))))

(def ^:dynamic ^:private *movie-map*)
(def ^:dynamic ^:private *person-map*)
(def ^:dynamic ^:private *genre-map*)
(def ^:dynamic ^:private *model*)

(def movietype-movie      (emf/eenum-literal 'MovieType.MOVIE))
(def movietype-tv         (emf/eenum-literal 'MovieType.TV))
(def movietype-video      (emf/eenum-literal 'MovieType.VIDEO))
(def movietype-videogame  (emf/eenum-literal 'MovieType.VIDEOGAME))

;; We don't parse TV serials.  Those line's have the series title enclosed in
;; "...".
(def movie-id-rx #"(([^\"]+)\s(\(\d\d\d\d(?:/[IVXCM]+)?\))(?:\s(\([A-Z]+\)))?(?:\s\{\{[^}]+\}\})?)")
(def movie-rx (re-pattern (str #"^" movie-id-rx #"\t+(\d\d\d\d)$")))
(defn parse-movies [file-name max-movie-count]
  (let [i (volatile! 0)
        ^java.io.Writer w (when +print-non-matching+
                            (io/writer "non-matching-movies.txt"))]
    (doseq [l (file-line-seq file-name)
            :while (or (== -1 max-movie-count)
                       (< @i max-movie-count))
            :let [match (re-matches movie-rx l)
                  _ (when (and +print-non-matching+
                               (not match)
                               (seq l)
                               (not= "\"" (subs l 0 1)))
                      (.append w ^String l)
                      (.append w "\n"))]
            :when match]
      (vswap! i inc)
      (let [[_ movie-id title year-n type year] match
            movie-id (str/trim movie-id)
            movie (g/create-element!
                   *model* 'Movie
                   {:title title
                    :year  (Integer/parseInt year)
                    :type  (case type
                             "(V)"  (g/enum-constant *model* 'MovieType.VIDEO)
                             "(VG)" (g/enum-constant *model* 'MovieType.VIDEOGAME)
                             "(TV)" (g/enum-constant *model* 'MovieType.TV)
                             nil    (g/enum-constant *model* 'MovieType.MOVIE))})]
        (swap! *movie-map* assoc movie-id movie)
        (when +verbose+
          (println (str "Movie: " title " (" year ") " type)))))
    @i))

(defn ^:private no-such-movie [movie-id when-parsing]
  (when (and +verbose+ (not= "\"" (subs movie-id 0 1)))
    (println (str "No such movie: <<<" movie-id ">>> (" when-parsing ")"))))

(def person-rx (re-pattern (str #"^([^\t]+)?\t+" movie-id-rx #".*")))
(defn parse-persons [file-name]
  (let [i (volatile! 0)
        cls (cond
             (re-matches #".*/actresses\..*" file-name) 'Actress
             (re-matches #".*/actors\..*"    file-name) 'Actor
             (re-matches #".*/directors\..*" file-name) 'Director)
        ^java.io.Writer w (when +print-non-matching+
                            (io/writer (format "non-matching-%s.txt"
                                               (case cls
                                                 Actor    "actors"
                                                 Actress  "actresses"
                                                 Director "directors"))))
        current-name   (volatile! nil)
        current-movies (volatile! #{})]
    (doseq [l (file-line-seq file-name)
            :let [match (re-matches person-rx l)
                  _ (when (and +print-non-matching+ (not match) (seq l))
                      (.append w ^String l)
                      (.append w "\n"))]
            :when match]
      (let [[_ person-name movie-id] match
            movie-id (str/trim movie-id)]
        (when person-name
          ;; New person starts, so persist the current one
          (when (and @current-name (seq @current-movies))
            (vswap! i inc)
            (locking *model*
              (let [p (g/create-element! *model* cls
                                         {:name   @current-name
                                          :movies @current-movies})
                    other (seq (filter (g/type-matcher *model*
                                                       (if (= cls 'Director)
                                                         'ActingPerson
                                                         'Director))
                                       (@*person-map* @current-name)))]
                (when other
                  (g/set-adj! p (if (= cls 'Director) :actingPerson :director) (first other)))
                (swap! *person-map* update @current-name conj p))
              (when +verbose+
                (println (str cls ": " @current-name " \t=> " (count @current-movies) " Movie(s)")))))
          (vreset! current-name  person-name)
          (vreset! current-movies #{}))
        (if-let [movie (@*movie-map* movie-id)]
          (vswap! current-movies conj movie)
          (no-such-movie movie-id cls))))
    @i))

(def ratings-rx (re-pattern (str #"\s+(?:[0-9.\*]+)\s+(?:\d+)\s+(\d+\.\d)\s+" movie-id-rx #"$")))
(defn parse-ratings [file-name]
  (let [i (volatile! 0)
        ^java.io.Writer w (when +print-non-matching+
                            (io/writer "non-matching-ratings.txt"))]
    (doseq [l (drop-while #(not= "MOVIE RATINGS REPORT" %)
                          (file-line-seq file-name))
            :let [match (re-matches ratings-rx l)
                  _ (when (and +print-non-matching+ (not match) (seq l))
                      (.append w ^String l)
                      (.append w "\n"))]
            :when match]
      (let [[_ rating movie-id] match
            movie-id (str/trim movie-id)]
        (if-let [movie (@*movie-map* movie-id)]
          (do
            (vswap! i inc)
            (locking *model*
              (g/set-aval! movie :rating (Double/parseDouble rating))
              (when +verbose+
                (println (str "Rating: " movie-id "\t=> " rating)))))
          (no-such-movie movie-id "Rating"))))
    @i))

(def genre-rx #"^([^\t]+)\t+([-\w]+)$")
(defn parse-genres [file-name]
  (let [i (volatile! 0)
        ^java.io.Writer w (when +print-non-matching+
                            (io/writer "non-matching-genres.txt"))
        get-genre (fn [genre-name]
                    (or (@*genre-map* genre-name)
                        (let [g (g/create-element! *model* 'Genre {:name genre-name})]
                          (swap! *genre-map* assoc genre-name g)
                          g)))]
    (doseq [l (drop-while  #(not= "8: THE GENRES LIST" %)
                           (file-line-seq file-name))
            :let [match (re-matches genre-rx l)
                  _ (when (and +print-non-matching+ (not match) (seq l))
                      (.append w ^String l)
                      (.append w "\n"))]
            :when match]
      (let [[_ movie-id genre-name] match
            movie-id (str/trim movie-id)]
        (if-let [movie (@*movie-map* movie-id)]
          (do
            (vswap! i inc)
            (locking *model*
              (g/add-adj! movie :genres (get-genre genre-name))
              (when +verbose+
                (println (str "Genre: " movie-id "\t=> " genre-name)))))
          (no-such-movie movie-id "Genre"))))
    @i))

(defn parse-imdb [kind dir max-movie-count]
  (binding [*model* (case kind
                      :tg (tg/new-graph (tg/load-schema (io/resource "movies.tg")))
                      :emf (emf/new-resource)
                      (u/errorf "kind must be :tg or :emf but was %." kind))
            *movie-map*  (atom {})
            *person-map* (atom {})
            *genre-map*  (atom {})]
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
