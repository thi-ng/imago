(ns imago.graph.memory
  (:require
   [imago.graph.api :as api]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [clojure.edn :as edn]
   [taoensso.timbre :refer [info warn error]]))

(defrecord MemoryGraph [conf g]
  api/ImagoGraph
  (update-user
    [_ user])
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
      (spit url (pr-str (trio/select @g)))
      (catch Exception e
        (warn "couldn't save graph" (.getMessage e))))
    _))

(defn graph-provider
  [conf]
  (-> (MemoryGraph. conf (ref (trio/plain-store)))
      (api/load-graph (-> conf :memory :path) nil)))
