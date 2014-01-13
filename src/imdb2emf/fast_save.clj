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

(defn escape-val [el attr]
  (-> (emf/eget el attr)
      (str/replace "&"  "&amp;")
      (str/replace "<"  "&lt;")
      (str/replace ">"  "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'"  "&apos;")
      (str/replace "\uFFFD" "&#xFFFD;")))

(poly/defpolyfn to-xml 'movies.Person [el pos-map]
  (str "  <movies:" (.getName (emf/eclass el))
       (when-let [ms (seq (emf/eget el :movies))]
         (str " movies=\"" (ref-list ms pos-map) "\""))
       " name=\"" (escape-val el :name) "\""
       "/>"))

(poly/defpolyfn to-xml 'movies.Movie [el pos-map]
  (str "  "
       "<movies:Movie "
       (when-let [ps (seq (emf/eget el :persons))]
         (str "persons=\"" (ref-list ps pos-map) "\" "))
       "title=\"" (escape-val el :title) "\" "
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
