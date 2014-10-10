(ns imago.config
  (:require
   [thi.ng.cljs.dom :as dom]))

(def app
  {:app-root (dom/by-id "imago-app")
   :routes   [{:match ["home"] :controller :home :route ["home"]}
              {:match ["login2"] :controller :login}]
   :default-route-id 0})

(defn set-config!
  [sym]
  (if-let [config (js/eval (aget js/window sym))]
    (set! app config)))

(defn timeout
  [id] 0)
