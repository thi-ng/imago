(ns thi.ng.cljs.utils
  (:require
   [goog.string.format]
   [clojure.string :as str]))

(def html-entities
  {\& "&amp;"
   \< "&lt;"
   \> "&gt;"
   \" "&quot;"})

(defn as-str [x]
  (if (or (keyword? x) (symbol? x))
    (name x)
    (str x)))

(defn escape-html
  [x] (str/escape (as-str x) html-entities))

(defn deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.

      (deep-merge-with +
        {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
        {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
      => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  ^{:author "Chris Houser"}
  [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))

(defn deep-merge
  [& maps] (apply deep-merge-with (fn[& maps] (last maps)) maps))

(defn parse-int
  ([x] (parse-int x 10 nil))
  ([x radix nf]
     (let [x' (js/parseInt x radix)]
       (if (js/isNaN x') nf x'))))

(defn parse-float
  ([x] (parse-float x nil))
  ([x nf]
     (let [x' (js/parseFloat x)]
       (if (js/isNaN x') nf x'))))

(defn now
  [] (.getTime (js/Date.)))

(defn float-formatter
  [prec]
  (fn [x] (.toFixed (js/Number. x) prec)))

(defn ->px [x] (str x "px"))

(defn format-date
  [d]
  (goog.string/format
   "%d-%02d-%02d"
   (.getFullYear d)
   (inc (.getMonth d))
   (.getDate d)))

(defn format-time
  [d]
  (goog.string/format
   "%02d:%02d:%02d"
   (.getHours d)
   (.getMinutes d)
   (.getSeconds d)))

(defn format-date-time
  [d] (str (format-date d) " " (format-time d)))

(defn- rand-bits [pow]
  (rand-int (bit-shift-left 1 pow)))

(defn new-uuid
  []
  (str
   (-> (js/Date.) (.getTime) (/ 1000) (Math/round) (.toString 16))
   "-" (-> (rand-bits 16) (.toString 16))
   "-" (-> (rand-bits 16) (bit-and 0x0FFF) (bit-or 0x4000) (.toString 16))
   "-" (-> (rand-bits 16) (bit-and 0x3FFF) (bit-or 0x8000) (.toString 16))
   "-" (-> (rand-bits 16) (.toString 16))
   (-> (rand-bits 16) (.toString 16))
   (-> (rand-bits 16) (.toString 16))))
