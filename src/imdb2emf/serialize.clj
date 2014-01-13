(ns imdb2emf.serialize
  (:require [clojure.java.io   :as io]
            [clojure.string    :as str]
            [imdb2emf.core     :as i2e]
            [funnyqt.protocols :as p]
            [funnyqt.emf       :as emf]
            [funnyqt.polyfns   :as poly])
  (:import (java.io DataOutputStream FileOutputStream Serializable
                    DataInputStream FileInputStream EOFException
                    BufferedInputStream BufferedOutputStream)))

(defn save-movies-model [model ^String file-name]
  (let [movie-pos-map (zipmap (emf/eallobjects model 'Movie)
                              (range))]
    (println "Saving model to" file-name)
    (with-open [oos (DataOutputStream. (BufferedOutputStream.
                                        (FileOutputStream. file-name)))]
      (doseq [movie (emf/eallobjects model 'Movie)]
        ;; tag: 0 => Movie
        (.writeByte oos 0)
        (.writeUTF oos (emf/eget movie :title))
        (.writeShort oos (emf/eget movie :year))
        (.writeByte oos (.getValue ^org.eclipse.emf.ecore.EEnumLiteral (emf/eget movie :type)))
        (.writeDouble oos (emf/eget movie :rating)))
      (doseq [person (emf/eallobjects model 'Person)]
        ;; tag: 1 => Actor, 2 => Actress
        (.writeByte oos (if (p/has-type? person 'Actor) 1 2))
        (.writeUTF oos (emf/eget person :name))
        (doseq [mov (emf/eget-raw person :movies)]
          (.writeInt oos (movie-pos-map mov)))
        ;; -1 terminates the references
        (.writeInt oos -1)))))

(defn read-movies-model [^String file-name]
  (let [movies (atom [])
        model  (emf/new-model)]
    (with-open [ois (DataInputStream. (BufferedInputStream.
                                       (FileInputStream. file-name)))]
      (letfn [(read-movie []
                (let [m (emf/ecreate! model 'Movie)]
                  (swap! movies conj m)
                  (emf/eadd! model m)
                  (emf/eset! m :title (.readUTF ois))
                  (emf/eset! m :year  (.readShort ois))
                  (emf/eset! m :type  (condp == (.readByte ois)
                                        0   i2e/movietype-movie
                                        1   i2e/movietype-tv
                                        2   i2e/movietype-video
                                        3   i2e/movietype-videogame))
                  (emf/eset! m :rating (.readDouble ois))))
              (read-person [cls]
                (let [p (emf/ecreate! model cls)]
                  (emf/eadd! model p)
                  (emf/eset! p :name (.readUTF ois))
                  (loop [i (.readInt ois)]
                    (when-not (== i -1)
                      (emf/eadd! p :movies (@movies i))
                      (recur (.readInt ois))))))]
        (try
          (while true
            (condp == (.readByte ois)
              0 (read-movie)
              1 (read-person 'Actor)
              2 (read-person 'Actress)))
          (catch EOFException _))))
    model))
