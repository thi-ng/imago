(ns imago.providers
  (:require
   [imago.config :as config]))

(defn get-provider
  [type]
  (let [ns (symbol (-> config/app type :impl-ns))]
    (require ns)
    (@(ns-resolve ns (symbol (str (name type) "-provider"))) (type config/app))))

(def storage (get-provider :storage))

(def graph (get-provider :graph))
