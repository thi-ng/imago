(ns imago.aws
  (:require
   [imago.config :as config]
   [amazonica.core :as aws]
   [amazonica.aws.s3 :as s3]
   [clojure.java.io :as io]
   [taoensso.timbre :refer [info warn error]])
  (:import
   [com.amazonaws.services.s3.model CannedAccessControlList]))

(def creds (select-keys (:aws config/app) [:access-key :secret-key]))

(def acl-presets
  {:public-read CannedAccessControlList/PublicRead})

(defn list-bucket
  ([] (let [c (:aws config/app)] (list-bucket (:bucket c) (:prefix c))))
  ([bucket prefix] (s3/list-objects creds bucket prefix)))

(defn put-file
  [src dest & [conf]]
  (let [{:keys [bucket prefix]} (or conf (:aws config/app))
        key (str prefix "/" dest)]
    (info "put-object" src ">>" key)
    (s3/put-object
     creds
     :bucket-name bucket
     :key key
     :metadata {:server-side-encryption "AES256"}
     :file (io/as-file src))
    (when-let [acl (acl-presets (:acl conf))]
      (s3/set-object-acl creds bucket key acl))))
