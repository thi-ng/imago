(ns imago.config
  (:require
   [environ.core :refer [env]]))

(def app
  {:storage
   {:file {:path       (or (env :imago-media-path) (str (System/getProperty "user.home") "/.imago"))}
    :aws  {:access-key (or (env :imago-aws-id) (env :aws-access-key-id))
           :secret-key (or (env :imago-aws-secret) (env :aws-secret-key))
           :bucket     (env :imago-s3-bucket)
           :prefix     (env :imago-s3-prefix)}}})
