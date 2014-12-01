(ns imdb2emf.serialize
  (:require [clojure.java.io   :as io]
            [clojure.string    :as str]
            [imdb2emf.core     :as i2e]
            [funnyqt.generic   :as g]
            [funnyqt.emf       :as emf])
  (:import (java.io DataOutputStream FileOutputStream Serializable
                    DataInputStream FileInputStream EOFException
                    BufferedInputStream BufferedOutputStream))
  (:gen-class
   :name imdb2emf.serialize.BinaryUtil
   :methods [^:static [readBinaryMoviesFile [String] org.eclipse.emf.ecore.resource.Resource]]))

(defn save-movies-model [model ^String file-name]
  (let [director-pos-map (zipmap (emf/eallcontents model 'Director)
                                 (range))
        genre-pos-map (zipmap (emf/eallcontents model 'Genre)
                              (range))
        movie-pos-map (zipmap (emf/eallcontents model 'Movie)
                              (range))]
    (println "Saving model to" file-name)
    (with-open [oos (DataOutputStream. (BufferedOutputStream.
                                        (FileOutputStream. file-name)))]
      (doseq [genre (emf/eallcontents model 'Genre)]
        ;; tag: 0 => Genre
        (.writeByte oos 0)
        (.writeUTF oos (emf/eget genre :name)))
      (doseq [movie (emf/eallcontents model 'Movie)]
        ;; tag: 1 => Movie
        (.writeByte oos 1)
        (.writeUTF oos (emf/eget movie :title))
        (.writeShort oos (emf/eget movie :year))
        (.writeByte oos (.getValue ^org.eclipse.emf.ecore.EEnumLiteral (emf/eget movie :type)))
        (.writeDouble oos (emf/eget movie :rating))
        (doseq [genre (emf/eget movie :genres)]
          (.writeInt oos (genre-pos-map genre)))
        ;; -1 terminates the references
        (.writeInt oos -1))
      ;; First the Directors, then the ActingPersons so that a actor who is
      ;; also an director references a Director that's already serialized
      ;; before himself.
      (doseq [person (concat (emf/eallcontents model 'Director)
                             (emf/eallcontents model 'ActingPerson))]
        ;; tag: 2 => Actor, 3 => Actress, 4 => Director
        (.writeByte oos (g/type-case person
                          Actor    2
                          Actress  3
                          Director 4))
        (.writeUTF oos (emf/eget person :name))
        (.writeInt oos (if-let [dir (and (g/has-type? person 'ActingPerson)
                                         (emf/eget person :director))]
                         (director-pos-map dir)
                         -1))
        (doseq [mov (emf/eget-raw person :movies)]
          (.writeInt oos (movie-pos-map mov)))
        ;; -1 terminates the references
        (.writeInt oos -1)))))

(defn read-movies-model [^String file-name]
  (let [directors (volatile! [])
        genres    (volatile! [])
        movies    (volatile! [])
        model     (emf/new-resource)]
    (with-open [ois (DataInputStream. (BufferedInputStream.
                                       (FileInputStream. file-name)))]
      (letfn [(read-genre []
                (let [g (emf/ecreate! model 'Genre)]
                  (vswap! genres conj g)
                  (emf/eset! g :name (.readUTF ois))))
              (read-movie []
                (let [m (emf/ecreate! model 'Movie)]
                  (vswap! movies conj m)
                  (emf/eset! m :title (.readUTF ois))
                  (emf/eset! m :year  (int (.readShort ois)))
                  (emf/eset! m :type  (condp == (.readByte ois)
                                        0   (emf/eenum-literal 'MovieType.MOVIE)
                                        1   (emf/eenum-literal 'MovieType.TV)
                                        2   (emf/eenum-literal 'MovieType.VIDEO)
                                        3   (emf/eenum-literal 'MovieType.VIDEOGAME)))
                  (emf/eset! m :rating (.readDouble ois))
                  (loop [i (.readInt ois)]
                    (when-not (== i -1)
                      (emf/eadd! m :genres (@genres i))
                      (recur (.readInt ois))))))
              (read-person [cls]
                (let [p (emf/ecreate! model cls)]
                  (when (= cls 'Director)
                    (vswap! directors conj p))
                  (emf/eset! p :name (.readUTF ois))
                  (let [dir-id (.readInt ois)]
                    (when-not (== dir-id -1)
                      (emf/eset! p :director (@directors dir-id))))
                  (loop [i (.readInt ois)]
                    (when-not (== i -1)
                      (emf/eadd! p :movies (@movies i))
                      (recur (.readInt ois))))))]
        (try
          (while true
            (condp == (.readByte ois)
              0 (read-genre)
              1 (read-movie)
              2 (read-person 'Actor)
              3 (read-person 'Actress)
              4 (read-person 'Director)))
          (catch EOFException _))))
    model))

(defn -readBinaryMoviesFile [file-name]
  (read-movies-model file-name))
