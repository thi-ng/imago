(ns imago.user
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :refer [<! timeout]]))

(defn show-template
  [state]
  (let [user (:user @state)
        uname (or (:name user) (:user-name user))]
    (->> (:app-root config/app)
         (dom/clear!)
         (dom/create-dom!
          [:div.jumbotron
           [:h1 (str "Welcome back, " uname)]
           [:p
            [:a.btn.btn-primary.btn-lg
             {:href "#/upload"}
             "Upload media"]]]))))

(defn init
  [bus]
  (let [init (async/subscribe bus :init-user)]
    (debug :init-user)
    (go-loop []
      (let [[_ [state]] (<! init)]
        (show-template state)
        (recur)))))
