(ns thi.ng.cljs.async
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.dom :as dom]
   [goog.events :as events]
   [cljs.core.async :as async :refer [<! >! chan put! close! timeout sliding-buffer]]))

(defprotocol PubSub
  (bus [_])
  (publisher [_])
  (publish [_ id body])
  (subscribe [_ id] [_ id sub])
  (unsubscribe [_] [_ id sub]))

(defn pub-sub
  [topic-fn]
  (let [bus (chan)
        pub (async/pub bus topic-fn)]
    (reify
      PubSub
      (bus [_] bus)
      (publisher [_] pub)
      (publish
        [_ id body] (put! bus [id body]))
      (subscribe
        [_ id] (subscribe _ id (chan)))
      (subscribe
        [_ id sub]
        (async/sub pub id sub)
        (debug :subscribed id)
        sub)
      (unsubscribe
        [_ id] (async/unsub-all pub id))
      (unsubscribe
        [_ id sub]
        (async/unsub pub id sub)
        (debug :unsubscribed id)))))

(defn unsubscribe-and-close-many
  [bus topic-map]
  (loop [coll topic-map]
    (if (seq coll)
      (let [[k v] (first coll)]
        (unsubscribe bus k v)
        (close! v)
        (recur (next coll))))))

(defn subscription-channels
  [bus ids]
  (reduce (fn [subs id] (assoc subs id (subscribe bus id))) {} ids))

(defn event-channel
  [el id & [f cap?]]
  (let [el (if (string? el) (dom/query nil el) el)
        ch (chan)
        handler (if f
                  (f ch)
                  (fn [e] (.preventDefault e) (put! ch e)))]
    (events/listen el id handler (or cap? false))
    [ch handler id el]))

(defn destroy-event-channel
  [[ch handler ev el]]
  (events/unlisten el ev handler)
  (close! ch))

(defn event-publisher
  [bus el ev id]
  (let [handler (fn [e] (publish bus id e))]
    (events/listen el ev handler)
    [el ev handler]))

(defn throttle
  [c ms]
  (let [c' (chan)]
    (go-loop []
      (when-let [x (<! c)]
        (>! c' x)
        (<! (timeout ms))
        (recur))
      (close! c))
    c'))

(defn sliding-channel
  [n] (chan (sliding-buffer n)))
