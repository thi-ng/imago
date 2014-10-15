(ns imago.config
  (:require
   [thi.ng.cljs.dom :as dom]))

(declare logged-in?)

(def perms
  {:create-coll "imago:CreateCollRights"
   :maintenance "imago:MaintenanceRights"})

(def ^:export app
  {:nav-root   (dom/by-id "imago-nav")
   :app-root   (dom/by-id "imago-app")
   :modal-root (dom/by-id "imago-modals")

   :nav-items
   {:anon [{:id :about :route "#/about" :label "About"}]
    :user [{:id :user :route "#/users/{{user}}" :label "My account"}
           {:id :about :route "#/about" :label "About"}]}

   :routes
   [{:match ["home"] :controller :home :route ["home"]}
    {:match ["about"] :controller :about}
    {:match ["collections" :id] :controller :collection}
    {:match ["media" :id] :controller :media}
    {:match ["users" :user] :controller :user}]
   :default-route-id 0

   :api-routes
   {:get-object (fn [id] (str "objects/" id))
    :user-collections (fn [user] (str "/user/" user "/collections"))
    :collection (fn [id] (str "/media/collections/" id))
    :login      (constantly "/user/login")
    :logout     (constantly "/user/logout")
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

(defn user-permitted?
  [user & ps]
  (if-let [uperms (:perms user)]
    (every? #(uperms (perms %)) ps)))
