(ns imago.graph.vocab
  (:require
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.vocabs :refer [defvocab]]
   [thi.ng.trio.vocabs.utils :as vu]
   [thi.ng.trio.vocabs.rdf]
   [thi.ng.trio.vocabs.rdfs]
   [thi.ng.trio.vocabs.owl]
   [thi.ng.trio.vocabs.dcterms]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def rdf thi.ng.trio.vocabs.rdf/rdf)

(def rdfs thi.ng.trio.vocabs.rdfs/rdfs)

(def owl thi.ng.trio.vocabs.owl/owl)

(def dcterms thi.ng.trio.vocabs.dcterms/dcterms)

(defvocab dctypes
  "http://purl.org/dc/dcmitype/"
  :Collection
  :MovingImage
  :RightsStatement
  :StillImage)

(defvocab foaf
  "http://xmlns.com/foaf/0.1/"
  :Agent
  :firstName
  :homepage
  :lastName
  :mbox
  :name
  :nick)

(def imago
  (-> "vocabs/imago.edn"
      io/resource
      vu/load-vocabs-as-model
      :imago))
