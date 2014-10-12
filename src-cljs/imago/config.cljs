(ns imago.config
  (:require
   [thi.ng.cljs.dom :as dom]))

(declare logged-in?)

(def app
  {:nav-root   (dom/by-id "imago-nav")
   :app-root   (dom/by-id "imago-app")
   :modal-root (dom/by-id "imago-modals")

   :nav-items
   {:anon []
    :user [{:id :user :route "#/user/{{user}}" :label "My account"}
           {:id :upload :route "#/upload" :label "Upload"}]}

   :routes
   [{:match ["home"] :controller :home :route ["home"]}
    {:match ["upload"] :controller :upload}
    {:match ["user" :user] :controller :user}
    {:match ["logout"] :controller :logout}]
   :default-route-id 0

   :api-routes
   {:get-object (fn [id] (str "objects/" id))
    :user-collections (fn [user] (str "/user/" user "/collections"))
    :login      (constantly "/user/login")
    :logout     (constantly "/user/logout")
    :upload     (fn [id] (str "/media/collections/" id))
    }

   :api-inject {}

   :timeouts
   {:controller 0}})

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

(defn logged-in?
  [state] (:user @state))
