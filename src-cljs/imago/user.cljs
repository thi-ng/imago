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
   [thi.ng.cljs.io :as io]
   [cljs.core.async :refer [<! timeout]]))

(defn show-template
  [state]
  (let [{:keys [name user-name] :as user} (:user @state)]
    (->> (:app-root config/app)
         (dom/clear!)
         (dom/create-dom!
          [:div.jumbotron
           [:h1 (str "Welcome back, " (or name user-name))]
           [:p "Here're your collections..."]
           (when (config/user-permitted? user :create-coll)
             [:p [:button.btn.btn-primary.btn-lg
                  {:events []}
                  [:span.glyphicon.glyphicon-plus] " New collection"]])]))))

(defn show-collections
  [state colls]
  (let [user (-> @state :user :user-name)]
    (->> (:app-root config/app)
         (dom/create-dom!
          [:div
           (for [{:keys [id title thumb]} colls]
             [:div.row
              [:div.col-xs-2 [:img {:src thumb}]]
              [:div.col-xs-6 [:a {:href (str "#/collections/" id)} [:h2 title]]]])]))))

(defn load-collections
  [state]
  (io/request
   :uri     (config/api-route :user-collections (-> @state :user :user-name))
   :method  :get
   :edn?    true
   :success (fn [status body]
              (info :success-response status body)
              (show-collections state (:body body)))
   :error   (fn [status body]
              (warn :error-response status body)
              (async/publish (:bus @state) :io-fail (:body body)))))

(defn init
  [bus]
  (let [init (async/subscribe bus :init-user)]
    (debug :init-user)
    (go-loop []
      (let [[_ [state]] (<! init)]
        (show-template state)
        (load-collections state)
        (recur)))))
