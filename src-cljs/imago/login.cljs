(ns imago.login
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [imago.modal :as modal]
   [imago.alerts :as alerts]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.io :as io]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :refer [<! alts! timeout]]))

(defn handle-register
  [bus data]
  (io/request
   :uri     (config/api-route :register)
   :method  :put
   :edn?    true
   :data    (config/inject-api-request-data data)
   :success (fn [status body]
              (info :success-response status body)
              (async/publish bus :register-success (:body body)))
   :error   (fn [status body]
              (warn :error-response status body)
              (async/publish bus :register-fail (:body body)))))

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

(defn handle-logout
  [bus]
  (io/request
   :uri     (config/api-route :logout)
   :method  :post
   :edn?    true
   :success (fn [status body]
              (info :success-response status body)
              (async/publish bus :logout-success (:body body)))
   :error   (fn [status body]
              (warn :error-response status body)
              (async/publish bus :logout-fail (:body body)))))

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

(defn register-dialog
  [bus]
  (modal/modal-dialog
   "Register new user"
   [:div
    [:div.form-group
     [:label {:for "name"} "Name"]
     [:input#fullname.form-control {:type "text" :placeholder "your name"}]]
    [:div.form-group
     [:label {:for "email"} "Email"]
     [:input#email.form-control {:type "email" :placeholder "your email"}]]
    [:div.form-group
     [:label {:for "username"} "User name"]
     [:input#username.form-control {:type "text" :placeholder "username"}]]
    [:div.form-group
     [:label {:for "pass1"} "Password"]
     [:input#pass1.form-control {:type "password" :placeholder "password"}]]
    [:div.form-group
     [:label {:for "pass2"} "Password (verify)"]
     [:input#pass2.form-control {:type "password" :placeholder "password"}]]]
   "Register"
   (fn [root]
     (let [form (map #(vector % (.-value (dom/by-id %)))
                     ["fullname" "email" "username" "pass1" "pass2"])]
       (info :form form)
       (handle-register bus form)
       (dom/clear! root))))
  (.focus (dom/by-id "fullname")))

(defn login-watcher
  [bus state]
  (let [subs  (async/subscription-channels
               bus [:login-success :login-fail :logout-success :logout-fail])
        chans (vec (vals subs))]
    (go-loop []
      (let [[[_ user] ch] (alts! chans)]
        (condp = ch
          (:login-success subs)  (do
                                   (info :user-logged-in user)
                                   (swap! state assoc :user user)
                                   (route/set-route! "users" (:user-name user)))
          (:login-fail subs)     (do
                                   (alerts/alert
                                    :danger
                                    [:div [:strong "Login failed!"] " Please try again..."]
                                    (:app-root config/app)))
          (:logout-success subs) (do
                                   (info :user-logged-out)
                                   (swap! state assoc :user user)
                                   (route/set-route! "/"))
          (:logout-fail subs)    (do
                                   (alerts/alert
                                    :danger
                                    [:div [:strong "Logout failed!"] " Please try again..."]
                                    (:app-root config/app))))
        (recur)))))
