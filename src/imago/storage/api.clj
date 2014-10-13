(ns imago.storage.api)

(defprotocol ImagoStorage
  (list-objects [_ re])
  (get-object [_ id])
  (put-object [_ src dest opts]))
