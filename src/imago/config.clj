(ns imago.config
  (:require
   [environ.core :refer [env]]))

(def app
  {:aws {:access-key (or (env :imago-aws-id) (env :aws-access-key-id))
         :secret-key (or (env :imago-aws-secret) (env :aws-secret-key))
         :bucket     (env :imago-s3-bucket)
         :prefix     (env :imago-s3-prefix)}})
