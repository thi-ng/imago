(ns imago.core
  (:require
   [imago.config :as config]
   [imago.providers :refer [graph]]
   [imago.graph.api :as gapi]
   [imago.graph.vocab :refer :all]
   [ring.util.response :as resp]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [taoensso.timbre :refer [info warn error]]))

(defn wrap-login-check
  [handler allowed-uris redirect-uri session-check]
  (fn [req]
    (if (or (session-check (:session req)) (allowed-uris (:uri req)))
      (handler req)
      (resp/redirect redirect-uri))))

(defroutes routes
  (GET "/" {:keys [session]}
       (let [count   (:count session 0)
             session (update-in session [:count] (fnil inc 0))]
         (-> (resp/response (str "You accessed this page " count " times." (-> config/app :aws :bucket)))
             (assoc :session session))))
  (GET "/login" [req]
       (-> (resp/response "login")))
  (POST "/login" {:keys [params]}
        (info params)
        (if-let [user (->> {:select :*
                            :query [{:where [['?u (:type rdf) (:User imago)]
                                             ['?u (:nick foaf) (:user params)]
                                             ['?u (:password foaf) (:pass params)]]}]}
                           (gapi/query graph)
                           (first))]
          (-> (resp/redirect "/")
              (assoc :session {:user (user '?u)}))
          (resp/redirect "/login")))
  (GET "/logout" [req]
       (-> (resp/response "logout")
           (assoc :session {})))
  (route/not-found "404"))

(def app
  (-> routes
      (wrap-login-check #{"/login" "/logout"} "/login" :user)))
