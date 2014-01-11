(ns imdb2emf.fast-save
  (:require [clojure.java.io   :as io]
            [clojure.string    :as str]
            [imdb2emf.core     :as i2e]
            [funnyqt.emf       :as emf]
            [funnyqt.polyfns   :as poly]))

(poly/declare-polyfn to-xml [el pos-map])

(defn ref-list [refs pos-map]
  (str/join " " (map #(str "/" (pos-map %))
                     refs)))

(defn person-props-to-xml [el pos-map]
  (str (when-let [ms (seq (emf/eget el :movies))]
         (str "movies=\"" (ref-list ms pos-map) "\" "))
       ;; Some Actors/Acresses have a REPLACEMENT CHARACTER in their name,
       ;; probably cause the IMDb files have a broken encoding.
       "name=\"" (str/replace (emf/eget el :name)
                              "\uFFFD" "&#xfffd;") "\""))

(poly/defpolyfn to-xml 'movies.Actor [el pos-map]
  (str "  <movies:Actor " (person-props-to-xml el pos-map) "/>"))

(poly/defpolyfn to-xml 'movies.Actress [el pos-map]
  (str "  <movies:Actress " (person-props-to-xml el pos-map) "/>"))

(poly/defpolyfn to-xml 'movies.Movie [el pos-map]
  (str "  "
       "<movies:Movie "
       (when-let [ps (seq (emf/eget el :persons))]
         (str "persons=\"" (ref-list ps pos-map) "\" "))
       "title=\"" (emf/eget el :title) "\" "
       "year=\"" (emf/eget el :year) "\""
       (let [t (emf/eget el :type)]
         (when (not= t i2e/movietype-movie)
           (str " type=\"" (.getLiteral ^org.eclipse.emf.ecore.EEnumLiteral t) "\"")))
       (let [r (emf/eget el :rating)]
         (when (not (== r 0.0))
           (str " rating=\"" r "\"")))
       "/>"))

(defn save-movies-model [model file-name]
  (let [pos-map (zipmap (emf/eallobjects model)
                        (range))]
    (println "Saving model to" file-name)
    (time
     (with-open [w (java.io.PrintWriter. (io/file file-name))]
       (.println w "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
       (.println w "<xmi:XMI xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\" xmlns:movies=\"http://movies/1.0\">")

       (doseq [el (emf/eallobjects model)]
         (.println w (to-xml el pos-map)))

       (.println w "</xmi:XMI>")))))
