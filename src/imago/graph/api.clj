(ns imago.graph.api
  (:require
   [imago.config :as config]
   [imago.utils :as utils]
   [imago.graph.vocab :refer :all]))

(defprotocol ImagoGraph
  (update-collection [_ coll])
  (get-collection [_ coll])
  (update-user [_ user])
  (get-user [_ id])
  (get-anon-user [_])
  (add-triples [_ triples])
  (query [_ q]))

(defprotocol ImagoGraphIO
  (load-graph [_ url opts])
  (save-graph [_ url opts]))

(defn pack-triples
  [triples]
  (->> triples
       (group-by first)
       (reduce-kv
        (fn [acc k v]
          (assoc acc k (mapv #(->> % (drop 1) vec) v)))
        {})))

(def queries
  {:login
    (fn [user pass salt]
      {:select '[{?id ?u} ?user-name ?name ?perms]
       :query [{:where [['?u (:type rdf) (:User imago)]
                        ['?u (:nick foaf) user]
                        ['?u (:passwordSha256Hash imago) (utils/sha-256 user pass salt)]
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
                           ['?thumb (:references dcterms) (-> config/version-presets :thumb-imago :id)]]}]
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
                        ['?preset (:format dcterms) '?mime]]}]})})

(defn query-spec
  [id & args] (apply (queries id) args))
