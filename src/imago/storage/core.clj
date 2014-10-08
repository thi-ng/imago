(ns imago.storage.core
  (:require
   [imago.config :as config]
   [imago.protocols :as proto]
   [environ.core :refer [env]]))

(def provider
  (let [ns (symbol (str "imago.storage." (env :imago-storage)))]
    (require ns)
    (@(ns-resolve ns 'storage-provider) (:storage config/app))))
