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

(defn handle-new-collection
  [state]
  (io/request
   :uri     (config/api-route :new-collection) ;; TODO coll spec incl. :method
   :method  :put
   :edn?    true
   :data    {}
   :success (fn [status body]
              (info :success-response status body)
              (async/publish (:bus state) :newcoll-success (:body body)))
   :error   (fn [status body]
              (warn :error-response status body)
              (async/publish (:bus state) :newcoll-fail (:body body)))))

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
                  {:events [[:click (fn [e] (handle-new-collection state))]]}
                  [:span.glyphicon.glyphicon-plus] " New collection"]])]))))

(defn show-collections
  [state colls]
  (let [user (-> @state :user :user-name)]
    (->> (:app-root config/app)
         (dom/create-dom!
          [:div
           (for [{:syms [?id ?title ?thumb]} colls
                 :let [thumb (config/api-route :image ?thumb)
                       coll  (str "#/collections/" ?id)]]
             [:div.row
              [:div.col-xs-4.col-md-2 (if ?thumb [:a {:href coll} [:img {:src thumb}]])]
              [:div.col-xs-8.col-md-10 [:a {:href coll} [:h2 ?title]]]])]))))

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
