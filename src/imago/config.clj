(ns imago.config
  (:require
   [imago.graph.vocab :refer :all]
   [environ.core :refer [env]]))

(def default-dir (str (System/getProperty "user.home") "/.imago"))

(def app
  {:graph
   {:impl-ns (or (env :imago-graph-impl) "image.graph.memory")
    :default-graph [{"b500e57b-4a61-4926-97d0-4077eb332d03"
                     {(:type rdf) (:User imago)
                      (:nick foaf) "admin"
                      (:password foaf) "imago"
                      (:hasRole imago) (:AdminRole imago)}}]
    :memory  {:path (or (env :imago-graph-path) (str default-dir "/graph.db"))}}

   :storage
   {:impl-ns (or (env :imago-media-storage) "imago.storage.file")
    :file    {:path       (or (env :imago-media-path) default-dir)}
    :aws     {:access-key (or (env :imago-aws-id) (env :aws-access-key-id))
              :secret-key (or (env :imago-aws-secret) (env :aws-secret-key))
              :bucket     (env :imago-s3-bucket)
              :prefix     (env :imago-s3-prefix)}}})
