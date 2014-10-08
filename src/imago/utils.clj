(ns imago.utils)

(defn new-uuid [] (str (java.util.UUID/randomUUID)))
