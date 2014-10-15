(ns imago.graph.memory
  (:require
   [imago.config :as config]
   [imago.graph.api :as api]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [clojure.edn :as edn]
   [taoensso.timbre :refer [info warn error]]))

(defrecord MemoryGraph [conf g]
  api/ImagoGraph
  (get-anon-user
    [_]
    (->> (config/query-spec :get-anon-user)
         (api/query _)
         (q/keywordize-result-vars)
         (first)))
  (update-user
    [_ user])
  (add-triples
    [_ triples]
    (dosync
     (info "adding triples:" (count triples))
     (ref-set g (trio/add-bulk @g triples))
     (api/save-graph _ (-> conf :memory :path) {})))
  (query [_ qspec]
    (q/query (assoc qspec :from @g)))

  api/ImagoGraphIO
  (load-graph
    [_ url opts]
    (dosync
     (try
       (info "reading graph from:" url)
       (ref-set g (trio/as-model (edn/read-string (slurp url))))
       (catch Exception e
         (warn "couldn't read graph:" (.getMessage e))
         (info "initializing default graph...")
         (ref-set g (trio/as-model (:default-graph conf))))))
    _)
  (save-graph
    [_ url opts]
    (try
      (info "saving graph to:" url)
      (->> @g
           (trio/select)
           (api/pack-triples)
           (pr-str)
           (spit url))
      (catch Exception e
        (warn "couldn't save graph" (.getMessage e))))
    _))

(defn graph-provider
  [conf]
  (-> (MemoryGraph. conf (ref (trio/plain-store)))
      (api/load-graph (-> conf :memory :path) nil)))
