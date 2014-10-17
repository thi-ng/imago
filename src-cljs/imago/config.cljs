(ns imago.config
  (:require
   [thi.ng.cljs.dom :as dom]))

(declare logged-in?)

(def perms
  {:create-coll "imago:canCreateColl"
   :create-user "imago:canCreateUser"
   :maintenance "imago:canEditRepo"})

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
   {:image            (fn [id] (str "/media/images/" id))
    :user-collections (fn [user] (str "/users/" user "/collections"))
    :new-collection   (constantly "/media/collections")
    :collection       (fn [id] (str "/media/collections/" id))
    :register         (constantly "/users")
    :login            (constantly "/users/login")
    :logout           (constantly "/users/logout")
    :current-user     (constantly "/users/session")
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
