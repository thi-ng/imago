(ns imago.graph.model
  (:require
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [thi.ng.trio.vocabs.utils :as vu]
   [thi.ng.validate.core :as v]
   [thi.ng.common.data.core :as d]
   [environ.core :refer [env]]
   [clojure.java.io :as io]
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
       (into {})
       (merge (select-keys rec (keys (apply dissoc rec :id (keys props)))))))

(defn pick-id
  [id] (fn [x] (let [k (id x)] (if (map? k) (:id k) k))))

(defn pick-id-coll
  [id]
  (fn [x]
    (let [k (id x)
          k (if (or (string? k) (number? k) (map? k)) [k] k)]
      (mapv #(if (map? %) (:id %) %) k))))

(defn wrap-optional [vals] (mapv #(v/optional %) vals))

(defn build-validators
  [props]
  (reduce-kv
   (fn [acc k {:keys [validate optional card]}]
     (if validate
       (let [v (if (and optional (not (map? validate)))
                 (wrap-optional validate)
                 validate)]
         (assoc acc k (if (and (= card :*) (not (map? v))) {:* v} v)))
       acc))
   {} props))

(defn build-sorter
  [id dir]
  (fn [props]
    (let [p (props id)]
      (if (coll? p)
        (vec (if (= :asc dir) (sort p) (reverse (sort p))))
        p))))

(defn build-initializers
  [props]
  (reduce-kv
   (fn [acc k {:keys [init card order]}]
     (let [ordered (if order (build-sorter k order))]
       (cond
        init        (assoc acc k (if order (fn [_] (-> _ order init)) init))
        ordered     (assoc acc k order)
        (= :* card) (assoc acc k (pick-id-coll k))
        :else       acc)))
   {} props))

(defn apply-initializers
  [props inits]
  (reduce-kv
   (fn [acc k v] (if (acc k) (assoc acc k (v acc)) acc))
   props inits))

(defn build-defaults
  [props]
  (reduce-kv
   (fn [acc k {:keys [card default]}]
     (cond
      default     (assoc acc k default)
      (= :* card) (assoc acc k (fn [_] []))
      :else       acc))
   {} props))

(defn inject-defaults
  [props defaults]
  (reduce-kv
   (fn [acc k v] (if (nil? (acc k)) (assoc acc k (if (fn? v) (v acc) v)) acc))
   props defaults))

(defn entity-map-from-triples
  [triples props id]
  (let [iprop (reduce-kv (fn [acc k v] (assoc acc (:prop v) k)) {} props)
        conj* (fnil conj [])]
    (->> triples
         (filter #(= id (:s %)))
         (reduce
          (fn [acc [_ p o]]
            (let [p' (iprop p p)
                  op (props p')]
              (if (and op (-> op :card (not= :*)))
                (assoc acc p' o)
                (update-in acc [p'] conj* o))))
          {:id id}))))

(defmacro defentity
  [name type props]
  (let [->sym      (comp symbol clojure.core/name)
        props      (merge {:type {:prop (:type rdf)}} props)
        fields     (cons 'id (map ->sym (keys props)))
        ctor-name  (utils/->kebab-case name)
        ctor       (symbol (str 'make- ctor-name))
        dctor      (symbol (str 'describe- ctor-name))
        dctor-as   (symbol (str 'describe-as- ctor-name))
        dctor-as2   (symbol (str 'describe-as- ctor-name '2))
        mctor      (symbol (str 'map-> name))]
    `(let [validators# (build-validators ~props)
           inits#      (build-initializers ~props)
           defaults#   (build-defaults ~props)]
       ;;(prn "-------- " ~type)
       ;;(prn :props ~props)
       ;;(prn :fields ~fields)
       ;;(prn :validators validators#)
       ;;(prn :inits inits#)
       ;;(prn :defaults defaults#)
       (defrecord ~name [~@fields]
         trio/PTripleSeq
         (~'triple-seq
           [_#] (filtered-triple-seq {~'id (triple-map ~props _#)})))

       (defn ~ctor
         {:doc ~(str "Constructs a new `" (namespace-munge *ns*) "." `~name "` entity from given map.\n"
                     "  Applies entity's property intializers, defaults & validation.\n"
                     "  In case of validation errors, throws map of errors using `slingshot/throw+`.")
          :arglists '([~'props])}
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
         {:doc ~(str "Executes a :describe query for given entity ID in graph.\n"
                     "  Returns seq of triples. ")
          :arglists '([~'graph ~'id])}
         [g# id#]
         (q/query
          {:describe '~'?id
           :from g#
           :query [{:where [['~'?id (-> ~props :type :prop) ~type]]}]
           :values {'~'?id #{id#}}}))

       (defn ~dctor-as
         {:doc ~(str "Constructs a new `" (namespace-munge *ns*) "." `~name "` entity based on a graph query\n"
                     "  for given entity ID and its specified props/rels (any additional rels will be included\n"
                     "  too, but are not validated during construction). If returned query results conflict\n"
                     "  with entity validation, throws map of errors using `slingshot/throw+`.")
          :arglists '([~'graph ~'id])}
         [g# id#]
         (-> (~dctor g# id#)
             (entity-map-from-triples ~props id#)
             (~ctor))))))

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
               :card :*
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
               :default (fn [_] [(utils/timestamp)])
               :card :*}
   :rights    {:prop (:accessRights dcterms)
               :init (pick-id-coll :rights)
               :validate [(v/uuid4)]
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
              :default (fn [_] [(utils/timestamp)])
              :card :*
              :order :asc}
   :repo     {:prop (:isPartOf dcterms)
              :validate [(v/uuid4)]
              :init (pick-id :repo)}
   :rights   {:prop (:accessRights dcterms)
              :default (fn [_] [])
              :validate {:* [(v/uuid4)]}
              :card :*}
   :media    {:prop (:hasPart dcterms)
              :validate {:* [(v/uuid4)]}
              :optional true
              :card :*}
   :presets  {:prop (:usesPreset imago)
              :validate [(v/uuid4)]
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
              :default (fn [{:keys [width height]}]
                         (if (and width height)
                           (str width "x" height)
                           "Untitled"))}
   :restrict {:prop (:restrict imago)
              :validate [(v/string)]
              :default (fn [_] ["image/png" "image/jpeg" "image/gif" "image/tiff"])
              :card :*}
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
                  :validate [(v/uuid4)]
                  :init (pick-id :user)
                  :optional true}
   :contributors {:prop (:contributor dcterms)
                  :validate [(v/uuid4)]
                  :card :*
                  :optional true}
   :publisher    {:prop (:publisher dcterms)
                  :validate [(v/uuid4)]
                  :init (pick-id :publisher)}
   :submitted    {:prop (:dateSubmitted dcterms)
                  :validate [(v/number) (v/pos)]
                  :default (fn [_] (utils/timestamp))}
   :colls        {:prop (:isPartOf dcterms)
                  :validate [(v/uuid4)]
                  :card :*}
   :versions     {:prop (:hasVersion dcterms)
                  :validate [(v/uuid4)]
                  :card :*
                  :optional true}})

(defentity ImageVersion (:ImageVersion imago)
  {:preset {:prop (:references dcterms)
            :validate [(v/uuid4)]
            :init (pick-id :preset)}
   :rights {:prop (:accessRights dcterms)
            :validate [(v/uuid4)]
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
     (:triples (vu/load-vocab-triples (io/resource "vocabs/imago.edn")))
     [admin anon]
     repo
     coll
     presets)))
