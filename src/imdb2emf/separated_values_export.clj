(ns imdb2emf.separated-values-export
  (:require [clojure.java.io    :as io]
            [clojure.string     :as str]
            [imdb2emf.core      :as i2e]
            [imdb2emf.serialize :as s]
            [funnyqt.emf        :as emf]
            [funnyqt.polyfns    :as poly]))

(def ^:const unit-sep \u001f)
(def ^:const record-sep \u001e)

(poly/declare-polyfn to-line [el])

(poly/defpolyfn to-line movies.Person [el]
  (str (.getName ^org.eclipse.emf.ecore.EClass (emf/eclass el))
       unit-sep
       (emf/eget el :name)
       record-sep))

(poly/defpolyfn to-line movies.Movie [el]
  (str "Movie"
       unit-sep
       (emf/eget el :title)
       unit-sep
       (emf/eget el :year)
       unit-sep
       (.getLiteral ^org.eclipse.emf.ecore.EEnumLiteral
                    (emf/eget el :type))
       unit-sep
       (emf/eget el :rating)
       unit-sep
       (str/join unit-sep
                 (for [p (emf/eget el :persons)]
                   (emf/eget p :name)))
       record-sep))

(defn save-movies-model [model file-name]
  (println "Saving model to" file-name)
  (with-open [w (java.io.PrintWriter. (io/file file-name))]
    (doseq [el (emf/eallobjects model 'Person)]
      (.println w (to-line el)))
    (doseq [el (emf/eallobjects model 'Movie)]
      (.println w (to-line el)))))
