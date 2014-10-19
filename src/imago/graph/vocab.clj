(ns imago.graph.vocab
  (:require
   [thi.ng.trio.core :as trio]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn load-vocab-triples
  [path]
  (->> path
       (io/resource)
       (slurp)
       (edn/read-string)
       (trio/triple-seq)
       (vec)))

(defn load-model
  [path]
  (->> path
       (io/resource)
       (slurp)
       (edn/read-string)
       (trio/as-model)))

(defn vocab-from-model
  [graph]
  (->> graph
       (trio/subjects)
       (map #(let [[_ v] (str/split % #":")] [(keyword v) %]))
       (into {})))

(defn make-vocab
  [prefix xs]
  (->> xs
       (map (fn [x] [x (str prefix x)]))
       (into {})))

(defmacro defvocab
  [id & xs]
  `(def ~id
     (let [xs# (list ~@xs)]
       (if (string? (first xs#))
         (-> xs# first load-model vocab-from-model)
         (make-vocab (name '~id) xs#)))))

(defvocab dcterms
  :abstract
  :accessRights
  :contributor
  :created
  :creator
  :dateSubmitted
  :description
  :format
  :hasPart
  :hasVersion
  :isPartOf
  :license
  :modified
  :publisher
  :references
  :rights
  :title)

(defvocab dctypes
  :Collection
  :MovingImage
  :RightsStatement
  :StillImage)

(defvocab rdf
  :Property
  :Resource
  :object
  :predicate
  :subject
  :type)

(defvocab rdfs
  :Class
  :subClasOf)

(defvocab foaf
  :Agent
  :firstName
  :homepage
  :lastName
  :mbox
  :name
  :nick)

(defvocab doap :Project)

(defvocab imago "vocabs/imago.edn")
