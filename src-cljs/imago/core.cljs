(ns imago.core
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [imago.home :as home]
   [imago.user :as user]
   [imago.collection :as coll]
   [imago.login :as login]
   [imago.alerts :as alerts]
   [thi.ng.domus.async :as async]
   [thi.ng.domus.io :as io]
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.domus.router :as router]
   [thi.ng.domus.utils :as utils]
   [thi.ng.domus.core :as dom]
   [thi.ng.domus.detect :as detect]
   [goog.events :as events]
   [clojure.string :as str]
   [cljs.reader :refer [read-string]]
   [cljs.core.async :refer [<! alts! timeout]]))

(def modules
  {:home       {:init home/init :enabled true}
   :user       {:init user/init}
   :collection {:init coll/init}
   :image      {}})

(defn build-nav
  [nav-routes sel-id user]
  (for [{:keys [id route label]} nav-routes
        :let [route (str/replace route "{{user}}" user)]]
    (if (= sel-id id)
      [:li.active [:a {:href route} label]]
      [:li [:a {:href route} label]])))

(defn show-nav
  [state sel-id]
  (let [user (-> @state :user)
        nav-routes (if-not (:anon user)
                     (-> config/app :nav-items :user)
                     (-> config/app :nav-items :anon))]
    (->> (:nav-root config/app)
         (dom/clear!)
         (dom/create-dom!
          [:div.navbar.navbar-default.navbar-static-top
           [:div.container
            [:div.navbar-header
             [:a.navbar-brand {:href "#/home"} "imago"]]
            [:div.navbar-collapse.collapse
             [:ul.nav.navbar-nav
              (build-nav nav-routes sel-id (:user-name user))]
             [:ul.nav.navbar-nav.navbar-right
              (if-not (:anon user)
                (list
                 [:li [:a "Logged in as " (:user-name user)]]
                 [:li [:a {:href "#"
                           :events [[:click
                                     (fn [e]
                                       (.preventDefault e)
                                       (login/handle-logout (:bus @state)))]]}
                       "Logout"]])
                [:li [:a {:href "#"
                          :events [[:click
                                    (fn [e]
                                      (.preventDefault e)
                                      (login/login-dialog (:bus @state)))]]}
                      "Login"]])]]]]))))

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
        (show-nav state new-id)
        (async/publish bus init-id [state params]))
      (do
        (warn "route handling module not configured:" new-id)
        (apply router/replace-route!
               (or (-> @state :route :route)
                   (get-in config/app [:routes (:default-route-id config/app) :route])))))))

(defn listen-route-change
  [state]
  (let [ch (async/subscribe (:bus @state) :route-changed)]
    (go-loop []
      (let [[_ new-route] (<! ch)]
        (debug :new-route new-route)
        (transition-controllers state new-route)
        (recur)))))

(defn listen-route-refresh
  [state]
  (let [ch (async/subscribe (:bus @state) :refresh-route)]
    (go-loop []
      (<! ch)
      (debug :refresh-route (:route @state))
      (transition-controllers state (:route @state))
      (recur))))

(defn listen-dom
  [bus]
  (dom/add-listeners
   [[js/window "resize"
     (fn [_]
       (async/publish
        bus :window-resize
        [(.-innerWidth js/window) (.-innerHeight js/window)]))]]))

(defn init-router
  [state routes default-route-id]
  (let [router (router/router
                routes default-route-id
                #(async/publish (:bus @state) :route-changed %))]
    (listen-route-change state)
    (listen-route-refresh state)
    (router/start! router)))

(defn make-app-state
  [bus user]
  (atom {:bus bus
         :user user
         :modules (reduce-kv
                   (fn [acc k {:keys [init enabled] :as v}]
                     (if-not (= false enabled)
                       (assoc acc k (assoc v :inited false))
                       acc))
                   {} modules)
         :route {:controller :loader}}))

(defn get-session-user
  [init]
  (io/request
   {:uri     (config/api-route :current-user)
    :method  :get
    :edn?    true
    :success (fn [status body]
               (info :success-response status body)
               (init (:body body)))
    :error   (fn [status body]
               (warn :error-response status body))}))
(defn start
  []
  (config/set-config! "__IMAGO_CONFIG__")
  (get-session-user
   (fn [user]
     (let [bus   (async/pub-sub (fn [e] (debug :bus (first e)) (first e)))
           state (make-app-state bus user)]
       (init-router state (:routes config/app) (:default-route-id config/app))
       (login/login-watcher bus state)))))

(.addEventListener js/window "load" start)
