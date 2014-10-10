(ns imago.core
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [imago.config :as config]
   [imago.home :as home]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.io :as io]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [thi.ng.cljs.detect :as detect]
   [goog.events :as events]
   [cljs.core.async :refer [<! timeout]]))

(def modules
  {:home {:init home/init :enabled true}})

(defn transition-dom
  [a b]
  #_(when-not (= a b)
      (let [ea (dom/by-id (name a))
            eb (dom/by-id (name b))
            dir (if (pos? (config/transition a b)) "next" "prev")]
        (dom/set-class! ea dir)
        (dom/set-class! eb "current"))))

(defn transition-controllers
  [state {new-id :controller params :params :as route}]
  (let [{bus :bus {curr-id :controller} :route} @state
        delay      (config/timeout :controller)
        init-id    (keyword (str "init-" (name new-id)))
        release-id (keyword (str "release-" (name curr-id)))]
    (if-let [module (-> @state :modules new-id)]
      (do
        (swap! state assoc :route (assoc route :last-route-change (utils/now)))
        (when-not (:inited module)
          (info :init-module new-id)
          ((:init module) bus)
          (swap! state assoc-in [:modules new-id :inited] true))
        (when (-> @state :modules curr-id)
          (if (= new-id curr-id)
            (async/publish bus release-id nil)
            (js/setTimeout #(async/publish bus release-id nil) delay)))
        (async/publish bus init-id [state params]))
      (do
        (warn "route handling module not configured:" new-id)
        (apply route/replace-route!
               (or (-> @state :route :route)
                   (get-in config/app [:routes (:default-route-id config/app) :route])))))))

(defn listen-route-change
  [bus]
  (let [ch (async/subscribe bus :route-changed)]
    (go
      (loop []
        (let [[_ [state new-route]] (<! ch)
              curr-id (-> @state :route :controller)
              new-id (:controller new-route)]
          (debug :new-route new-route)
          (transition-controllers state new-route)
          (transition-dom curr-id new-id)
          (recur))))))

(defn listen-dom
  [bus]
  (dom/add-listeners
   [[js/window "resize"
     (fn [_]
       (async/publish
        bus :window-resize
        [(.-innerWidth js/window) (.-innerHeight js/window)]))]]))

(defn init-router
  [bus state routes default-route-id]
  (let [router (route/router
                routes (routes default-route-id)
                #(async/publish bus :route-changed [state %]))]
    (listen-route-change bus)
    (route/start-router! router)))

(defn make-app-state
  [bus]
  (atom {:bus bus
         :modules (reduce-kv
                   (fn [acc k {:keys [init enabled] :as v}]
                     (if-not (= false enabled)
                       (assoc acc k (assoc v :inited false))
                       acc))
                   {} modules)
         :route {:controller :loader}}))

(defn start
  []
  (config/set-config! "__IMAGO_CONFIG__")
  (let [bus   (async/pub-sub (fn [e] (debug :bus (first e)) (first e)))
        state (make-app-state bus)]
    (init-router
     bus state
     (:routes config/app) (:default-route-id config/app))
    (dom/create-dom!
     [:a {:href "#/login2"} "Login"]
     (dom/by-id "imago-app"))))

(.addEventListener js/window "load" start)
