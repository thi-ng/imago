(ns imago.core
  (:require
   [imago.config :as config]
   [imago.providers :refer [graph]]
   [imago.graph.api :as gapi]
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [ring.util.response :as resp]
   [ring.middleware.defaults :as rd]
   [compojure.core :refer [defroutes context routes GET POST]]
   [compojure.route :as route]
   [hiccup.page :refer [html5 include-js include-css]]
   [taoensso.timbre :refer [info warn error]]))

(def page-cache
  {:home (html5
          [:head
           [:title "Imago"]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
           (apply include-css (-> config/app :ui (config/mode) :css))]
          [:body
           [:div#imago-app [:h1 "imago"]]
           (apply include-js (-> config/app :ui (config/mode) :js))])})

(defn wrap-login-check
  [handler allowed-uris redirect-uri session-check]
  (fn [req]
    (if (or (session-check (:session req)) (allowed-uris (:uri req)))
      (handler req)
      (resp/redirect redirect-uri))))

(def user-routes
  (routes
   (POST "/login" {{:keys [user pass]} :params}
         (info "login attempt:" user pass)
         (if-let [user (->> {:select :*
                             :query [{:where [['?u (:type rdf) (:User imago)]
                                              ['?u (:nick foaf) user]
                                              ['?u (:password foaf) (utils/sha-256 user pass config/salt)]]}]}
                            (gapi/query graph)
                            (first))]
           (-> (resp/redirect "/")
               (assoc :session {:user (user '?u)}))
           {:status 403}))
   (POST "/logout" [req]
         (-> (resp/response "logout")
             (assoc :session {})))))

(defroutes all-routes
  (GET "/" [] (:home page-cache))
  (GET "/test" {:keys [session]}
       (let [count   (:count session 0)
             session (update-in session [:count] (fnil inc 0))]
         (-> (resp/response (str "You accessed this page " count " times." (-> config/app :aws :bucket)))
             (assoc :session session))))
  (context "/user" [] user-routes)
  (route/not-found "404"))

(def app
  (-> all-routes
      ;;(wrap-login-check #{"/" "/login" "/logout"} "/login" :user)
      (rd/wrap-defaults (assoc-in rd/site-defaults [:security :anti-forgery] false))))
