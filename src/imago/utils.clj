(ns imago.utils
  (:import
   [java.security NoSuchAlgorithmException MessageDigest]))

(defn new-uuid [] (str (java.util.UUID/randomUUID)))

(defn sha-256
  [& input]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes (apply str input)))
    (let [digest (.digest md)]
      (apply str (mapv #(format "%02x" (bit-and % 0xff)) digest)))))

