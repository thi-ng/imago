(ns imago.core
  (:require
   [imago.config :as config]
   [imago.providers :refer [graph]]
   [imago.graph.api :as gapi]
   [imago.graph.vocab :refer :all]
   [imago.utils :as utils]
   [thi.ng.validate.core :as v]
   [ring.util.response :as resp]
   [ring.util.codec :as codec]
   [ring.middleware.defaults :as rd]
   [compojure.core :refer [defroutes context routes GET POST]]
   [compojure.route :as route]
   [hiccup.page :refer [html5 include-js include-css]]
   [hiccup.element :as el]
   [clojure.edn :as edn]
   [clojure.data.json :as json]
   [taoensso.timbre :refer [info warn error]]))

(def page-cache
  {:home (html5
          [:head
           [:title "imago"]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
           (apply include-css (-> config/app :ui (config/mode) :css))]
          [:body
           [:div#imago-nav]
           [:div#imago-app.container]
           [:div#imago-modals]
           (apply include-js (-> config/app :ui (config/mode) :js))
           (el/javascript-tag
            (str "var __IMAGO_CONFIG__="
                 (or (-> config/app :ui (config/mode) :override-config) "null") ";"))])})

(defn request-signature
  [uri params key]
  (->> key
       (str uri ";" (codec/form-encode params) ";" )
       (utils/sha-256)))

(defn validate-params
  [params validators]
  (v/validate params validators))

(defn valid-accept?
  [req types]
  (let [^String accept (get-in req [:headers "accept"])]
    (prn :req req)
    (or (= accept "*/*")
        (some #(utils/str-contains? accept %) types))))

(defn validate-api-params
  [params id]
  (validate-params params (get-in config/app [:validators :api id])))

(defn valid-api-accept?
  [req] (valid-accept? req config/api-mime-types))

(defn valid-signature?
  [{:keys [uri form-params]}]
  (let [hash (request-signature uri (dissoc form-params "sig") "key") ;; FIXME use sign-key from session
        sig  (form-params "sig")]
    (= sig hash)))

(defn basic-api-response-body
  [data status]
  (if (< status 400)
    {:status "ok" :body data}
    {:status "error" :errors data}))

(defn api-response
  [req data status]
  (let [accept (get-in req [:headers "accept"])
        {:keys [edn json text]} config/mime-types
        body (basic-api-response-body data status)
        [body type] (cond
                     (or (= accept "*/*") (utils/str-contains? accept edn))
                     [(pr-str body) edn]

                     (utils/str-contains? accept json)
                     [(json/write-str body :escape-slash false) json]

                     :else [(pr-str body) text])]
    (-> (resp/response body)
        (resp/status status)
        (resp/content-type type))))

(defn invalid-api-response
  []
  (-> (apply str
             "Only the following content types are supported: "
             (interpose ", " config/api-mime-types))
      (resp/response)
      (resp/status 406)
      (resp/content-type (:text config/mime-types))))

(defn invalid-signature-response
  []
  (-> (resp/response "Request signature check failed")
      (resp/status 403)
      (resp/content-type (:text config/mime-types))))

(defn missing-entity-response
  [req id] (api-response req {:reason (str "Unknown ID: " id)} 404))

(defn new-entity-request
  [req validate-id handler]
  (if (valid-api-accept? req)
    (let [[params err] (validate-api-params (:form-params req) validate-id)]
      (if (nil? err)
        (try
          (handler req params)
          (catch Exception e
            (.printStackTrace e)
            (api-response req "Error saving entity" 500)))
        (api-response req err 400)))
    (invalid-api-response)))

(def user-routes
  (routes
   (POST "/login" [:as req]
         (if (valid-api-accept? req)
           (let [[{:strs [user pass]} err] (validate-api-params (:form-params req) :login)]
             (info "login attempt:" user pass)
             (if (nil? err)
               (if-let [user (->> {:select :*
                                   :query [{:where [['?u (:type rdf) (:User imago)]
                                                    ['?u (:nick foaf) user]
                                                    ['?u (:password foaf) (utils/sha-256 user pass config/salt)]]}
                                           {:optional [['?u (:name foaf) '?n]]}]}
                                  (gapi/query graph)
                                  (first))]
                 (-> (api-response req {:user-id (user '?u) :name (user '?n)} 200)
                     (assoc :session {:user (user '?u)}))
                 (api-response req "invalid login" 403))
               (api-response req err 400)))
           (invalid-api-response)))
   (POST "/logout" [:as req]
         (-> (api-response req "user logged out" 200)
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
      (rd/wrap-defaults (assoc-in rd/site-defaults [:security :anti-forgery] false))))
