(ns imago.model
  (:require
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [thi.ng.trio.core :as trio]
   [thi.ng.validate.core :as v]
   [slingshot.slingshot :refer [throw+]]))

(defrecord User
    [id type
     user-name name
     mbox password homepage
     role
     created]
  trio/PTripleSeq
  (triple-seq
    [_]
    (->> {id
          {(:type rdf) type
           (:nick foaf) user-name
           (:name foaf) name
           (:mbox foaf) mbox
           (:password foaf) password
           (:created dcterms) created
           (:hasRole imago) role
           (:homepage foaf) homepage}}
         (trio/triple-seq)
         (filter last))))

(defn make-user
  [{:keys [id type role created]
    :or {id      (utils/new-uuid)
         type    (:AnonUser imago)
         role    (:AnonRole imago)
         created (utils/timestamp)}
    :as opts}]
  (let [[opts err]
        (-> opts
            (merge {:id id :type type :role role :created created})
            (v/validate
             {:id       [(v/uuid4)]
              :type     [(v/member-of #{(:User imago) (:AnonUser imago)})]
              :role     [(v/member-of #{(:AdminRole imago) (:UserRole imago) (:AnonRole imago)})]
              :created  [(v/number) (v/pos)]
              :mbox     [(v/optional (v/mailto))]
              :password [(v/optional (v/fixed-length 64))] ;; sha-256 = 32 bytes
              :homepage [(v/optional (v/url))]}))]
    (if err
      (throw+ err)
      (map->User opts))))

(defrecord MediaRepository
    [id created modified rights]
  trio/PTripleSeq
  (triple-seq
    [_]
    (->> {id
          {(:type rdf) (:Repository imago)
           (:created dcterms) created
           (:modified dcterms) modified
           (:accessRights dcterms) rights}}
         (trio/triple-seq)
         (filter last))))

(defn make-repo
  [{:keys [id created modified rights]
    :or   {id       (utils/new-uuid)
           created  (utils/timestamp)
           modified (utils/timestamp)}
    :as   opts}]
  (map->MediaRepository
   (assoc opts :id id :created created :modified modified)))

(defrecord Collection
    [id type title creator created modified parent rights presets]
  trio/PTripleSeq
  (triple-seq
    [_]
    (->> {id
          {(:type rdf) type
           (:title dcterms) title
           (:creator dcterms) creator
           (:created dcterms) created
           (:modified dcterms) modified
           (:isPartOf dcterms) parent
           (:accessRights dcterms) rights
           (:usesPreset imago) presets}}
         (trio/triple-seq)
         (filter last))))

(defn make-collection
  [{:keys [id type title created]
    :or   {id      (utils/new-uuid)
           type    (:MediaCollection imago)
           title   "Untitled"
           created (utils/timestamp)}
    :as opts}]
  (map->Collection
   (assoc opts :id id :type type :title title :created created)))

(defrecord RightsStatement
    [id user perm context]
  trio/PTripleSeq
  (triple-seq
    [_]
    (->> {id
          {(:type rdf) (:RightsStatement dctypes)
           (:subject rdf) user
           (:predicate rdf) perm
           (:object rdf) context}}
         (trio/triple-seq))))

(defn make-rights-statement
  [{:keys [id user perm context]
    :or   {id (utils/new-uuid)}
    :as   opts}]
  (map->RightsStatement (assoc opts :id id)))

(defrecord ImageVersionPreset
    [id title restrict width height crop filter mime]
  trio/PTripleSeq
  (triple-seq
    [_]
    (->> {id
          {(:type rdf) (:ImageVersionPreset imago)
           (:title dcterms) title
           (:restrict imago) restrict
           (:width imago) width
           (:height imago) height
           (:crop imago) (boolean crop)
           (:filter imago) filter
           (:format dcterms) mime}}
         (trio/triple-seq)
         (clojure.core/filter last))))

(defn make-version-preset
  [{:keys [id title restrict mime]
    :or   {id       (utils/new-uuid)
           title    "Untitled preset"
           restrict ["image/png" "image/jpeg"]
           mime     "image/jpeg"}
    :as   opts}]
  (map->ImageVersionPreset
   (assoc opts :id id :title title :restrict restrict :mime mime)))