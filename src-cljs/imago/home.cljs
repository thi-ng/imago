(ns imago.home
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [imago.login :as login]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :refer [<! timeout]]))

(defn show-template
  [state]
  (->> (:app-root config/app)
       (dom/clear!)
       (dom/create-dom!
        [:div.jumbotron
         [:h1 "Welcome to imago"]
         [:p "Graph all your media!"]
         (when (config/user-permitted? (:user @state) :create-user)
           [:p [:a.btn.btn-primary.btn-lg
                {:href "#/register"
                 :events [[:click (fn [e] (login/register-dialog (:bus @state)))]]}
                "Register"]])])))

(defn init
  [bus]
  (let [init (async/subscribe bus :init-home)]
    (debug :home-init)
    (go-loop []
      (let [[_ [state]] (<! init)]
        (show-template state)
        (recur)))))
