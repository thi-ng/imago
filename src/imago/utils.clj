(ns imago.utils
  (:require
   [clojure.string :as str]
   [clj-time.core :as cljt]
   [clj-time.coerce :as cljtc])
  (:import
   [java.security NoSuchAlgorithmException MessageDigest]))

(defn ->kebab-case
  [x] (-> x (str/replace #"([a-z\d])([A-Z])" "$1-$2") str/lower-case))

(defn new-uuid [] (str (java.util.UUID/randomUUID)))

(defn sha-256
  [& input]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes (apply str input)))
    (let [digest (.digest md)]
      (apply str (mapv #(format "%02x" (bit-and % 0xff)) digest)))))

(defn str-contains?
  [^String str ^String x] (not (neg? (.indexOf str x))))

(defn timestamp [] (-> (cljt/now) cljtc/to-long))
