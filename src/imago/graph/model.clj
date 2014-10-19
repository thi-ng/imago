(ns imago.graph.model
  (:require
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [thi.ng.validate.core :as v]
   [thi.ng.common.data.core :as d]
   [environ.core :refer [env]]
   [slingshot.slingshot :refer [throw+]]))

(def salt (or (env :imago-salt) "969a606798c94d03b53c3fa5e83b4594"))

(defn filtered-triple-seq
  [xs] (->> xs (trio/triple-seq) (filter #(-> % last nil? not))))

(defn triple-map
  [props rec]
  (->> props
       (map
        (fn [[k f]]
          [(:prop f)
           (if-let [ser (:values f)]
             (ser (k rec))
             (k rec))]))
       (into {})))

(defn extract-key
  [props key]
  (reduce-kv
   (fn [acc k v]
     (if-let [v (key v)]
       (assoc acc k v)
       acc))
   {} props))

(defn wrap-optional [vals] (mapv #(v/optional %) vals))

(defn build-validators
  [props]
  (->> (extract-key props :validate)
       (reduce-kv
        (fn [acc k v]
          (assoc
              acc k
              (if (and (-> props k :optional) (not (map? v)))
                (wrap-optional v)
                ;;(reduce-kv (fn [a' k' v'] (assoc a' k' (wrap-optional v'))) v v)
                v)))
        {})))

(defn apply-initializers
  [props inits]
  (reduce-kv
   (fn [acc k v] (if (acc k) (assoc acc k (v acc)) acc))
   props inits))

(defn inject-defaults
  [props defaults]
  (reduce-kv
   (fn [acc k v] (if (nil? (acc k)) (assoc acc k (if (fn? v) (v acc) v)) acc))
   props defaults))

(defn pick-id
  [id] (fn [x] (let [k (id x)] (if (map? k) (:id k) k))))

(defn pick-id-coll
  [id]
  (fn [x]
    (let [k (id x)]
      (if (map? (first k)) (mapv :id k) (vec k)))))

(defn build-patterns
  [props]
  (mapv (fn [[k v]] ['?id (:prop v) (symbol (str \? (name k)))]) props))

(defn build-describe-spec
  [g id props]
  (let [[opt req] (d/bisect (fn [[_ v]] (:optional v)) props)
        patterns [{:where (build-patterns req)}]
        patterns (if (seq opt)
                   (->> opt
                        (build-patterns)
                        (map (fn [p] {:optional [p]}))
                        (into patterns))
                   patterns)]
    {:select :*
     :from g
     :query patterns
     :values {'?id #{id}}}))

(defn prepare-describe-results
  [props res]
  (reduce-kv
   (fn [acc k v]
     (let [p (keyword (q/qvar-name k))]
       (assoc acc p
              (if (and (== 1 (count v)) (-> props p :card (not= :*)))
                (first v)
                (vec v)))))
   {} res))

(defmacro defentity
  [name rdf-type props]
  (let [name       name
        type       rdf-type
        ->sym      (comp symbol clojure.core/name)
        props      (assoc props :type {:prop "rdf:type"})
        fields     (cons 'id (map ->sym (keys props)))
        ctor-name  (utils/->kebab-case name)
        ctor       (symbol (str 'make- ctor-name))
        dctor      (symbol (str 'describe- ctor-name))
        dctor-as   (symbol (str 'describe-as- ctor-name))
        mctor      (symbol (str 'map-> name))]
    `(let [;;validators# (extract-key ~props :validate)
           validators# (build-validators ~props)
           defaults#   (extract-key ~props :default)
           inits#      (extract-key ~props :init)]
       ;;(prn "-------- " ~type)
       ;;(prn :props props)
       ;;(prn :fields fields)
       ;;(prn :validators validators#)
       ;;(prn :inits inits)
       ;;(prn :defaults defaults)
       (defrecord ~name [~@fields]
         trio/PTripleSeq
         (~'triple-seq
           [_#] (filtered-triple-seq {~'id (triple-map ~props _#)})))
       (defn ~ctor
         [opts#]
         (let [[opts# err#]
               (-> opts#
                   (assoc :id (or (:id opts#) (utils/new-uuid)))
                   (assoc :type (or (:type opts#) ~type))
                   (apply-initializers inits#)
                   (inject-defaults defaults#)
                   (v/validate validators#))]
           (if (nil? err#) (~mctor opts#) (throw+ err#))))
       (defn ~dctor
         [g# id#]
         (q/query
          {:describe '~'?id
           :from g#
           :query [{:where [['~'?id "rdf:type" ~type]]}]
           :values {'~'?id #{id#}}}))
       (defn ~dctor-as
         [g# id#]
         (->> ~props
              (build-describe-spec g# id#)
              (q/query)
              (q/accumulate-result-vars)
              ;;((fn [x#] (prn x#) x#))
              (prepare-describe-results ~props)
              (~ctor)))
       )))

(defentity User (:User imago)
  {:user-name {:prop (:nick foaf)
               :validate [(v/string) (v/min-length 3) (v/max-length 32)]}
   :name      {:prop (:name foaf)
               :validate [(v/string) (v/max-length 64)]
               :optional true}
   :mbox      {:prop (:mbox foaf)
               :validate [(v/mailto)]
               :optional true}
   :homepage  {:prop (:homepage foaf)
               :validate [(v/url)]
               :optional true}
   :password  {:prop (:passwordSha256Hash imago)
               :validate [(v/string) (v/fixed-length 64)]
               :init (fn [{:keys [user-name password]}]
                       (if (= 64 (count password))
                         password
                         (utils/sha-256 user-name password salt)))
               :optional true}
   :created   {:prop (:created dcterms)
               :validate [(v/number) (v/pos)]
               :default (fn [_] (utils/timestamp))}})

(defentity Repository (:Repository imago)
  {:created   {:prop (:created dcterms)
               :validate [(v/number) (v/pos)]
               :default (fn [_] (utils/timestamp))}
   :modified  {:prop (:modified dcterms)
               :validate [(v/number) (v/pos)]
               :default (fn [_] (utils/timestamp))}
   :rights    {:prop (:accessRights dcterms)
               :init (pick-id-coll :rights)
               :default (fn [_] [])
               :validate {:* [(v/uuid4)]}
               :card :*}})

(defentity Collection (:MediaCollection imago)
  {:title    {:prop (:title dcterms)
              :validate [(v/string)]
              :default (fn [_] "Untitled collection")}
   :creator  {:prop (:creator dcterms)
              :validate [(v/uuid4)]
              :init (pick-id :creator)}
   :created  {:prop (:created dcterms)
              :validate [(v/number) (v/pos)]
              :default (fn [_] (utils/timestamp))}
   :modified {:prop (:modified dcterms)
              :validate [(v/number) (v/pos)]
              :init (fn [{:keys [modified]}]
                      (if (coll? modified)
                        (last (sort modified))
                        modified))
              :default (fn [_] (utils/timestamp))}
   :repo     {:prop (:isPartOf dcterms)
              :validate [(v/uuid4)]
              :init (pick-id :repo)}
   :rights   {:prop (:accessRights dcterms)
              :init (pick-id-coll :rights)
              :default (fn [_] [])
              :validate {:* [(v/uuid4)]}
              :card :*}
   :media    {:prop (:hasPart dcterms)
              :init (pick-id-coll :media)
              :default (fn [_] [])
              :validate {:* [(v/uuid4)]}
              :optional true
              :card :*}
   :presets  {:prop (:usesPreset imago)
              :init (pick-id-coll :presets)
              :default (fn [_] [])
              :validate {:* [(v/uuid4)]}
              :card :*}})

(defentity RightsStatement (:RightsStatement dctypes)
  {:user    {:prop (:subject rdf)
             :validate [(v/uuid4)]
             :init (pick-id :user)}
   :perm    {:prop (:predicate rdf)
             :validate [(v/required)]}
   :context {:prop (:object rdf)
             :validate [(v/uuid4)]
             :init (pick-id :context)}})

(defentity ImageVersionPreset (:ImageVersionPreset imago)
  {:title    {:prop (:title dcterms)
              :validate [(v/string)]
              :default (fn [{:keys [width height]}] (str width "x" height))}
   :restrict {:prop (:restrict imago)
              :validate {:* [(v/string)]}
              :default (fn [_] ["image/png" "image/jpeg"])}
   :width    {:prop (:width imago)
              :validate [(v/number) (v/pos)]
              :optional true}
   :height   {:prop (:height imago)
              :validate [(v/number) (v/pos)]
              :optional true}
   :crop     {:prop (:crop imago)
              :validate [(v/boolean (constantly false))]
              :optional true}
   :filter   {:prop (:filter imago)
              :validate [(v/string)]
              :optional true}
   :mime     {:prop (:format dcterms)
              :validate [(v/string)]
              :default (fn [_] "image/jpeg")}})

(defentity StillImage (:StillImage dctypes)
  {:title        {:prop (:title dcterms)
                  :validate [(v/string)]
                  :default (fn [_] "Untitled")}
   :creator      {:prop (:creator dcterms)
                  :validate [(v/optional (v/uuid4))]
                  :init (pick-id :user)}
   :contributors {:prop (:contributor dcterms)
                  :init (pick-id-coll :contributors)
                  :default (fn [_] [])
                  :validate {:* [(v/uuid4)]}
                  :card :*}
   :publisher    {:prop (:publisher dcterms)
                  :validate [(v/uuid4)]
                  :init (pick-id :publisher)}
   :submitted    {:prop (:dateSubmitted dcterms)
                  :validate [(v/number) (v/pos)]
                  :default (fn [_] (utils/timestamp))}
   :colls        {:prop (:isPartOf dcterms)
                  :validate {:* [(v/uuid4)]}
                  :init (pick-id-coll :colls)
                  :default (fn [_] [])
                  :card :*}
   :versions     {:prop (:hasVersion dcterms)
                  :init (pick-id-coll :versions)
                  :default (fn [_] [])
                  :validate {:* [(v/uuid4)]}
                  :card :*}})

(defentity ImageVersion (:ImageVersion imago)
  {:preset {:prop (:references dcterms)
            :validate [(v/uuid4)]
            :init (pick-id :preset)}
   :rights {:prop (:accessRights dcterms)
            :init (pick-id-coll :rights)
            :default (fn [_] [])
            :validate {:* [(v/uuid4)]}
            :card :*}})

(defn add-entity-rights
  [e rights]
  (->> rights
       (reduce
        (fn [acc [user perms]]
          (reduce
           (fn [[e rs] p]
             (let [r (make-rights-statement {:user user :perm p :context (:id e)})]
               [(update-in e [:rights] conj (:id r))
                (conj rs r)]))
           acc (if (coll? perms) perms [perms])))
        [e []])
       (apply cons)))

(defn make-repository-with-rights
  [repo rights]
  (-> repo make-repository (add-entity-rights rights)))

(defn make-collection-with-rights
  [coll rights]
  (-> coll make-collection (add-entity-rights rights)))

(defn make-image-version-with-rights
  [v rights]
  (-> v make-image-version (add-entity-rights rights)))

(defn default-graph
  [{:keys [presets mime-types]}]
  (let [presets  (map
                  (fn [[k {:keys [mime filter crop] :as v}]]
                    (->> {:title (name k)
                          :mime (mime-types mime)
                          :filter (name (or filter :none))
                          :crop (boolean crop)}
                         (merge v)
                         (make-image-version-preset)))
                  presets)
        admin    (make-user
                  {:user-name "admin"
                   :name "Imago Admin"
                   :password "imago"})
        anon     (make-user
                  {:type (:AnonUser imago)
                   :user-name "anon"})
        repo     (make-repository-with-rights
                  {}
                  {(:id admin) [(:canEditRepo imago) (:canCreateColl imago)]
                   (:id anon) [(:canViewRepo imago) (:canCreateUser imago)]})
        coll     (make-collection-with-rights
                  {:creator (:id admin)
                   :repo (:id (first repo))
                   :presets (map :id presets)}
                  {(:id admin) (:canEditColl imago)
                   (:id anon) (:canViewColl imago)})]
    (concat
     (load-vocab-triples "vocabs/imago.edn")
     [admin anon]
     repo
     coll
     presets)))
