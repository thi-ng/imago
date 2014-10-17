(ns imago.config
  (:require
   [imago.graph.vocab :refer :all]
   [imago.graph.model :as model]
   [imago.utils :as utils]
   [thi.ng.validate.core :as v]
   [environ.core :refer [env]]))

(def mode        (or (env :imago-deploy-mode) :dev))
(def salt        (or (env :imago-salt) "969a606798c94d03b53c3fa5e83b4594"))
(def default-dir (str (System/getProperty "user.home") "/.imago"))

(def mime-types
  {:edn    "application/edn"
   :json   "application/json"
   :png    "image/png"
   :jpg    "image/jpeg"
   :svg    "image/svg+xml"
   :text   "text/plain"
   :stl    "application/sla"
   :binary "application/octet-stream"})

(def mime-ext
  (zipmap
   (sort (vals mime-types))
   [".edn" ".json" ".bin" ".stl" ".jpg" ".png" ".svg" ".txt"]))

(def api-mime-types
  (vals (select-keys mime-types [:edn :json])))

(def version-presets
  {:thumb-imago {:id "617e6192-d1a3-4422-b3cc-d7fcfb782de5"
                 :width 160 :height 160 :crop true :mime :jpg}
   :thumb-gray  {:id "581cfd98-32f4-4c5d-845c-29c45539cf7e"
                 :width 200 :height 200 :crop true :filter :grayscale :mime :jpg}
   :thumb-rgb   {:id "b23dd8c0-10d6-4b2d-99b0-5a39101691a3"
                 :width 200 :height 200 :crop true :mime :jpg}
   :small       {:id "296e1cfd-9fb7-4af7-9714-57f177e60ad5"
                 :width 320 :height 240 :mime :jpg}
   :hd360       {:id "3c253c5d-a0cb-42df-964d-8167ddae818f"
                 :width 640 :height 360 :crop true :mime :jpg}
   :hd720-crop  {:id "fd9e54e5-3700-4736-ba32-a1bae45cf0b3"
                 :width 1280 :height 720 :crop true :mime :jpg}
   :hd720       {:id "adf8457f-64bf-4875-a713-faa8063eaba7"
                 :height 720 :mime :jpg}
   :hd1280      {:id "5d7f5d6b-c210-49c1-9e3e-59c4d15b18cd"
                 :width 1280 :mime :jpg}})

(def app
  {:graph
   {:impl-ns (or (env :imago-graph-impl) "imago.graph.memory")
    :default-graph (model/default-graph
                     {:presets version-presets
                      :mime-types mime-types
                      :salt salt})
    :salt    salt
    :memory  {:path (or (env :imago-graph-path) (str default-dir "/graph.db"))}}

   :storage
   {:impl-ns (or (env :imago-media-storage) "imago.storage.file")
    :file    {:path       (or (env :imago-media-path) default-dir)}
    :aws     {:access-key (or (env :imago-aws-id) (env :aws-access-key-id))
              :secret-key (or (env :imago-aws-secret) (env :aws-secret-key))
              :bucket     (env :imago-s3-bucket)
              :prefix     (env :imago-s3-prefix)}}

   :validators
   {:api  {:login
           {:user [(v/required) (v/max-length 16)]
            :pass [(v/required) (v/min-length 4) (v/max-length 40)]}
           :upload
           {:user [(v/required)]
            :coll-id [(v/required) (v/uuid4)]}
           :get-collection
           {:coll-id [(v/required) (v/uuid4)]}
           :new-collection
           {:user {:perms [(v/required-keys ["imago:canCreateColl"] "insufficient permission")]}
            :title [(v/optional (v/max-length 64))]}}}

   :queries
   {:login
    (fn [user pass]
      {:select '[{?id ?u} ?user-name ?name ?perms]
       :query [{:where [['?u (:type rdf) (:User imago)]
                        ['?u (:nick foaf) user]
                        ['?u (:password foaf) (utils/sha-256 user pass salt)]
                        ['?repo (:type rdf) (:Repository imago)]
                        ['?repo (:accessRights dcterms) '?r]
                        ['?r (:subject rdf) '?u]
                        ['?r (:predicate rdf) '?p]]}
               {:optional [['?u (:name foaf) '?name]]}]
       :bind {'?user-name (constantly user)}
       :aggregate {'?perms {:use '?p :fn #(into #{} %)}}})

    :get-repo
    (fn []
      {:select '?repo
       :query [{:where [['?repo (:type rdf) (:Repository imago)]]}]})

    :get-anon-user
    (fn []
      {:select ['{?id ?u} '?user-name '?n '?perms {'?anon (constantly true)}]
       :query [{:where [['?u (:type rdf) (:AnonUser imago)]
                        ['?u (:nick foaf) '?user-name]
                        ['?repo (:type rdf) (:Repository imago)]
                        ['?repo (:accessRights dcterms) '?rs]
                        ['?rs (:subject rdf) '?u]
                        ['?rs (:predicate rdf) '?p]]}
               {:optional [['?u (:name foaf) '?name]]}]
       :aggregate {'?perms {:use '?p :fn #(into #{} %)}}})

    :get-user-collections
    (fn [user curr-user]
      {:select '[?id ?title ?thumb ?date ?perms]
       :query [{:where [['?u (:type rdf) (:User imago)]
                        ['?u (:nick foaf) user]
                        ['?id (:creator dcterms) '?u]
                        ['?id (:type rdf) (:MediaCollection imago)]
                        ['?id (:title dcterms) '?title]]}
               {:optional [['?id (:accessRights dcterms) '?r]
                           ['?r (:subject rdf) '?curr-u]
                           ['?r (:predicate rdf) '?p]]}
               {:optional [['?au (:type rdf) (:AnonUser imago)]
                           ['?id (:accessRights dcterms) '?r]
                           ['?r (:subject rdf) '?au]
                           ['?r (:predicate rdf) '?p]]}
               {:optional [['?ra (:type rdf) (:RightsStatement dctypes)]
                           ['?ra (:subject rdf) '?curr-u]
                           ['?ra (:predicate rdf) '?p]]
                :values {'?p #{(:canEditRepo imago)}}}
               {:optional [['?img (:isPartOf dcterms) '?id]
                           ['?img (:hasVersion dcterms) '?thumb]
                           ['?img (:dateSubmitted dcterms) '?date]
                           ['?thumb (:references dcterms) (-> version-presets :thumb-imago :id)]]}]
       :group '?id
       :aggregate {'?perms {:use '?p :fn #(into #{} (filter identity %))}}
       :order-desc '?date
       :values {'?curr-u #{curr-user}}
       :filter {'?perms #(do (prn :filter %) (seq %))}})
    :get-collection
    (fn [coll-id]
      {:select :*
       :query [{:where [[coll-id (:type rdf) (:MediaCollection imago)]
                        [coll-id (:title dcterms) '?title]
                        ['?id (:type rdf) (:StillImage imago)]
                        ['?id (:isPartOf dcterms) coll-id]
                        ['?id (:hasVersion dcterms) '?version]
                        ['?version (:references dcterms) '?preset]
                        ['?preset (:width imago) '?w]
                        ['?preset (:height imago) '?h]
                        ['?preset (:format dcterms) '?mime]]}]
       :group '?id})

    :describe-collection
    (fn [user coll-id]
      {:describe '[?x ?i ?v ?r]
       :query [{:where [['?x (:type rdf) (:MediaCollection imago)]]}
               {:optional [['?x (:accessRights dcterms) '?r]
                           ['?r (:subject rdf) user]]}
               {:optional [['?r (:subject rdf) user]
                           ['?r (:predicate rdf) (:canEditRepo imago)]]}
               {:optional [['?i (:isPartOf dcterms) '?x]
                           ['?i (:hasVersion dcterms) '?v]]}]
       :values {'?x #{coll-id}}})

    :collection-presets
    (fn [coll-id]
      {:select :*
       :query [{:where [[coll-id (:type rdf) (:MediaCollection imago)]
                        [coll-id (:usesPreset imago) '?preset]
                        ['?preset (:restrict imago) '?src-mime]
                        ['?preset (:crop imago) '?crop]
                        ['?preset (:filter imago) '?filter]
                        ['?preset (:format dcterms) '?mime]]}
               {:optional [['?preset (:width imago) '?w]]}
               {:optional [['?preset (:height imago) '?h]]}]
       :group '?preset
       :aggregate {'?restrict {:use '?src-mime :fn #(into #{} %)}}})

    :media-item-version
    (fn [version]
      {:select '[?id ?mime]
       :query [{:where [['?id (:type rdf) (:StillImage dctypes)]
                        ['?id (:hasVersion dcterms) version]
                        [version (:references dcterms) '?preset]
                        ['?preset (:format dcterms) '?mime]]}]})}
   :ui
   {:dev  {:css ["/css/bootstrap.min.css"
                 "/css/bootstrap-theme.min.css"
                 "/css/imago.css"]
           :js  ["/js/app.js"
                 "/lib/sha256.js"]
           :override-config "'imago.config.app'"}
    :prod {:css ["/css/bootstrap.min.css"
                 "/css/bootstrap-theme.min.css"
                 "/css/imago.css"]
           :js  ["/js/app.min.js"
                 "/lib/sha256.js"]}}})

(defn query-spec
  [id & args] (apply (-> app :queries id) args))
