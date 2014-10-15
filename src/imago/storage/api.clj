(ns imago.storage.api)

(defprotocol ImagoStorage
  (list-objects [_ re])
  (get-object [_ id])
  (get-object-response [_ id])
  (put-object [_ src dest opts]))
