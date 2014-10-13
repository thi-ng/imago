(ns imago.core
  (:require
   [imago.config :as config]
   [imago.providers :refer [storage graph]]
   [imago.graph.api :as gapi]
   [imago.graph.vocab :refer :all]
   [imago.storage.api :as sapi]
   [imago.image :as image]
   [imago.utils :as utils]
   [thi.ng.validate.core :as v]
   [thi.ng.trio.query :as q]
   [ring.util.response :as resp]
   [ring.util.codec :as codec]
   [ring.middleware.defaults :as rd]
   [compojure.core :refer [defroutes context routes GET POST]]
   [compojure.route :as route]
   [hiccup.page :refer [html5 include-js include-css]]
   [hiccup.element :as el]
   [clojure.edn :as edn]
   [clojure.data.json :as json]
   [taoensso.timbre :refer [info warn error]])
  (:import
   [java.io File]))

(def page-cache
  {:home
   (html5
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

(defn wrapped-api-handler
  [req params validate-id handler]
  (if (valid-api-accept? req)
    (let [[params err] (if validate-id
                         (validate-api-params params validate-id)
                         [params])]
      (if (nil? err)
        (try
          (handler req params)
          (catch Exception e
            (.printStackTrace e)
            (api-response req "Error handling route" 500)))
        (api-response req err 400)))
    (invalid-api-response)))

(def user-routes
  (routes
   (POST "/login" [:as req]
         (info "login attempt:" (:params req))
         (wrapped-api-handler
          req (:params req) :login
          (fn [req {:keys [user pass]}]
            (let [user' (->> (config/query-spec :login user pass)
                             (gapi/query graph)
                             (first))]
              (info :found-user user')
              (if user'
                (let [user' {:id (user' '?u)
                             :user-name user
                             :name (user' '?n)
                             :perms (user' '?perms)}]
                  (-> (api-response req user' 200)
                      (assoc :session {:user user'})))
                (api-response req "invalid login" 403))))))
   (POST "/logout" [:as req]
         (wrapped-api-handler
          req nil nil
          (fn [req _]
            (-> (api-response req "user logged out" 200)
                (assoc :session {})))))
   (GET "/:user/collections" [user :as req]
        (wrapped-api-handler
         req {:user user} :get-user-collections ;; TODO
         (fn [req _]
           (let [colls (->> (config/query-spec :get-user-collections user)
                            (gapi/query graph)
                            (q/keywordize-result-vars))]
             (info :user-colls colls)
             (api-response req colls 200)))))
   ))

(def media-routes
  (routes
   (GET "/collections/:coll-id" [coll-id :as req]
        (wrapped-api-handler
         req nil nil
         (fn [req params]
           (let [items (->> (config/query-spec :get-collection coll-id))]
             (api-response req items 200)))))
   (POST "/collections/:coll-id" [coll-id :as req]
         (info req)
         (wrapped-api-handler
          req (assoc (:params req) :user (get-in req [:session :user])) :upload
          (fn [req params]
            (let [user-id (-> params :user :id)
                  tstamp  (utils/timestamp)
                  files (filter :tempfile (vals (:params req)))
                  presets (->> (config/query-spec :collection-presets coll-id)
                               (gapi/query graph)
                               (vals)
                               (map first))
                  tmp (File/createTempFile "imago" nil)
                  _ (info :files (map :filename files))
                  _ (info :presets presets)
                  _ (info :tmp-file tmp)
                  [ids triples] (->>
                                 (for [[id file] (zipmap (repeatedly utils/new-uuid) files)
                                       {:syms [?w ?h ?mime ?preset ?crop ?filter]} presets]
                                   [id (:tempfile file) tmp ?w ?h ?crop ?filter ?mime ?preset])
                                 (reduce
                                  (fn [[ids triples] [id src dest w h crop filter mime preset]]
                                    (let [version (str id "-" preset)]
                                      (image/resize-image
                                       {:src src
                                        :dest dest
                                        :type (subs (config/mime-ext mime) 1)
                                        :width w
                                        :height h
                                        :crop crop
                                        :filter filter})
                                      (sapi/put-object storage dest (str version (config/mime-ext mime)) {})
                                      [(conj ids id)
                                       (into triples
                                             [[id (:type rdf) (:StillImage imago)]
                                              [id (:isPartOf dct) coll-id]
                                              [id (:creator dct) user-id]
                                              [id (:dateSubmitted dct) tstamp]
                                              [id (:hasVersion dct) version]
                                              [version (:references dct) preset]])]))
                                  [#{} []]))]
              (gapi/add-triples graph triples)
              (api-response req (vec ids) 200)))))))

(defroutes all-routes
  (GET "/" [] (:home page-cache))
  (context "/user" [] user-routes)
  (context "/media" [] media-routes)
  (route/not-found "404"))

(def app
  (-> all-routes
      (rd/wrap-defaults (assoc-in rd/site-defaults [:security :anti-forgery] false))))
