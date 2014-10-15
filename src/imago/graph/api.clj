(ns imago.graph.api)

(defprotocol ImagoGraph
  (update-collection [_ coll])
  (get-collection [_ coll])
  (update-user [_ user])
  (get-user [_ id])
  (get-anon-user [_])
  (add-triples [_ triples])
  (query [_ q]))

(defprotocol ImagoGraphIO
  (load-graph [_ url opts])
  (save-graph [_ url opts]))

(defn pack-triples
  [triples]
  (->> triples
       (group-by first)
       (reduce-kv
        (fn [acc k v]
          (assoc acc k (mapv #(->> % (drop 1) vec) v)))
        {})))
