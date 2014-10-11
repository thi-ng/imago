(ns imago.config
  (:require
   [thi.ng.cljs.dom :as dom]))

(def app
  {:nav-root   (dom/by-id "imago-nav")
   :app-root   (dom/by-id "imago-app")
   :modal-root (dom/by-id "imago-modals")
   
   :nav-items  [{:id :home :route "#/home" :label "Home"}
                {:id :upload :route "#/upload" :label "Upload"}]

   :routes     [{:match ["home"] :controller :home :route ["home"]}
                {:match ["upload"] :controller :upload}]
   :default-route-id 0

   :api-routes {:get-object (fn [id] (str "objects/" id))
                :login      (constantly "/user/login")}

   :api-inject {}
   
   :timeouts   {:controller 0}})

(defn set-config!
  [sym]
  (if-let [config (js/eval (aget js/window sym))]
    (set! app config)))

(defn api-route
  [id & args] (apply (-> app :api-routes id) args))

(defn inject-api-request-data
  [data]
  (merge (-> app :api-inject) data))

(defn timeout
  [id] (-> app :timeouts id))
