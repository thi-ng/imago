(ns imago.core
  (:require
   [imago.config :as config]
   [ring.util.response :as resp]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]))

(defroutes app
  (GET "/" {:keys [session]}
       (let [count   (:count session 0)
             session (update-in session [:count] (fnil inc 0))]
         (-> (resp/response (str "You accessed this page " count " times." (-> config/app :aws :bucket)))
             (assoc :session session))))
  (route/not-found "404"))
