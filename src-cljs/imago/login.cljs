(ns imago.login
  (:require
   [imago.config :as config]
   [imago.modal :as modal]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.io :as io]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :refer [<! timeout]]))

(defn handle-login
  [bus user pass]
  (let [data {:user user :pass pass}]
    (io/request
     :uri     (config/api-route :login)
     :method  :post
     :edn?    true
     :data    (config/inject-api-request-data data)
     :success (fn [status body]
                (info :success-response status body)
                (async/publish bus :login-success (:body body)))
     :error   (fn [status body]
                (warn :error-response status body)
                (async/publish bus :login-fail (:body body))))))

(defn login-dialog
  [bus]
  (modal/modal-dialog
   "Login"
   [:div
    [:div.form-group
     [:label {:for "username"} "User name"]
     [:input#username.form-control {:type "text" :placeholder "username"}]]
    [:div.form-group
     [:label {:for "password"} "Password"]
     [:input#password.form-control {:type "password" :placeholder "password"}]]]
   "Login"
   (fn [root]
     (let [user (.-value (dom/by-id "username"))
           pass (.-value (dom/by-id "password"))]
       (handle-login bus user pass)
       (dom/clear! root))))
  (.focus (dom/by-id "username")))
