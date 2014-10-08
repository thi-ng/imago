(ns imago.protocols)

(defprotocol ImagoStorage
  (list-objects [_ re])
  (put-object [_ src dest opts]))
