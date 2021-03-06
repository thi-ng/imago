(ns imago.config
  (:require
   [thi.ng.domus.core :as dom]))

(declare logged-in?)

(def perms
  {:create-coll "http://imago.thi.ng/owl/canCreateColl"
   :create-user "http://imago.thi.ng/owl/canCreateUser"
   :edit-coll   "http://imago.thi.ng/owl/canEditColl"
   :maintenance "http://imago.thi.ng/owl/canEditRepo"})

(def presets
  {:imago-thumb "617e6192-d1a3-4422-b3cc-d7fcfb782de5"
   :imago-xl    "fd9e54e5-3700-4736-ba32-a1bae45cf0b3"})

(def ^:export app
  {:nav-root   (dom/by-id "imago-nav")
   :app-root   (dom/by-id "imago-app")
   :modal-root (dom/by-id "imago-modals")

   :nav-items
   {:anon [{:id :about :route "#/about" :label "About"}]
    :user [{:id :user :route "#/users/{{user}}" :label "My account"}
           {:id :about :route "#/about" :label "About"}]}

   :routes
   [{:match ["home"] :controller :home :id :home}
    {:match ["about"] :controller :about :id :home}
    {:match ["collections" :id] :controller :collection :id :collection}
    {:match ["media" :id] :controller :media :id :media}
    {:match ["users" :user] :controller :user :id :user}]
   :default-route-id :home

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
