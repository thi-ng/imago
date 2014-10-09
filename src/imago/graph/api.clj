(ns imago.graph.api)

(defprotocol ImagoGraph
  (update-collection [_ coll])
  (get-collection [_ coll])
  (update-user [_ user])
  (get-user [_ id])
  (add-triples [_ triples])
  (query [_ q]))

(defprotocol ImagoGraphIO
  (load-graph [_ url opts])
  (save-graph [_ url opts]))
