(ns imago.config
  (:require
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [environ.core :refer [env]]))

(def default-dir (str (System/getProperty "user.home") "/.imago"))

(def default-graph
  (let [presets {:thumb      {:id "581cfd98-32f4-4c5d-845c-29c45539cf7e"
                              :width 200 :height 200 :crop true}
                 :small      {:id "296e1cfd-9fb7-4af7-9714-57f177e60ad5"
                              :width 320 :height 240}
                 :hd360      {:id "3c253c5d-a0cb-42df-964d-8167ddae818f"
                              :width 640 :height 360 :crop true}
                 :hd720-crop {:id "fd9e54e5-3700-4736-ba32-a1bae45cf0b3"
                              :width 1280 :height 720 :crop true}
                 :hd720      {:id "adf8457f-64bf-4875-a713-faa8063eaba7"
                              :width 1280 :height 720}}
        admin   (utils/new-uuid)]
    [{admin
      {(:type rdf) (:User imago)
       (:nick foaf) "admin"
       (:password foaf) "imago"
       (:hasRole imago) (:AdminRole imago)}}
     {(utils/new-uuid)
      {(:type rdf) (:Collection imago)
       (:title dc) "Untitled collection"
       (:creator dc) admin
       (:usesPreset imago) (map :id (vals presets))}}
     (reduce-kv
      (fn [acc k {:keys [id width height crop]}]
        (conj acc [id {"rdf:type" (:ImageVersionPreset imago)
                       "dc:title" (name k)
                       "imago:width" width
                       "imago:height" height
                       "imago:crop" (boolean crop)}]))
      {} presets)]))

(def app
  {:graph
   {:impl-ns (or (env :imago-graph-impl) "image.graph.memory")
    :default-graph default-graph
    :memory  {:path (or (env :imago-graph-path) (str default-dir "/graph.db"))}}

   :storage
   {:impl-ns (or (env :imago-media-storage) "imago.storage.file")
    :file    {:path       (or (env :imago-media-path) default-dir)}
    :aws     {:access-key (or (env :imago-aws-id) (env :aws-access-key-id))
              :secret-key (or (env :imago-aws-secret) (env :aws-secret-key))
              :bucket     (env :imago-s3-bucket)
              :prefix     (env :imago-s3-prefix)}}})
