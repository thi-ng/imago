(ns imago.graph.vocab
  (:require
   [thi.ng.trio.core :as trio]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn load-vocab
  [path]
  (->> path
       (io/resource)
       (slurp)
       (edn/read-string)
       (trio/as-model)
       (trio/subjects)
       (map #(let [[_ v] (str/split % #":")] [(keyword v) %]))
       (into {})))

(defn make-vocab
  [prefix xs]
  (->> xs
       (map (fn [x] [x (str prefix x)]))
       (into {})))

(defmacro defvocab
  [id & xs] `(def ~id (make-vocab (name '~id) (list ~@xs))))

(defvocab dct :abstract :accessRights :creator :contributor :description :isPartOf :license :references :title)

(defvocab rdf :type :Property :Resource)

(defvocab foaf :Agent :name :nick :mbox :password)

(defvocab doap :Project)

(defvocab owl :Thing :Class :SubClass)

(def imago (load-vocab "vocabs/imago.edn"))
