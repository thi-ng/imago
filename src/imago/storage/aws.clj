(ns imago.storage.aws
  (:require
   [imago.storage.api :as api]
   [amazonica.core :as aws]
   [amazonica.aws.s3 :as s3]
   [clojure.java.io :as io]
   [taoensso.timbre :refer [info warn error]])
  (:import
   [com.amazonaws.services.s3.model CannedAccessControlList]))

(def acl-presets
  {:public-read CannedAccessControlList/PublicRead})

(defn storage-provider
  [conf]
  (let [creds (select-keys (:aws conf) [:access-key :secret-key])
        {:keys [bucket prefix]} (:aws conf)]
    (info "using S3 storage provider @" bucket prefix)
    (reify api/ImagoStorage
      (list-objects
        [_ re]
        (s3/list-objects creds bucket))
      (put-object
        [_ src dest opts]
        (let [key (str prefix "/" dest)]
          (info "put-object" src ">>" key)
          (s3/put-object
           creds
           :bucket-name bucket
           :key key
           :metadata {:server-side-encryption "AES256"}
           :file (io/as-file src))
          (when-let [acl (acl-presets (:acl opts))]
            (s3/set-object-acl creds bucket key acl)))))))
