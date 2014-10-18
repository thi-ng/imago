(ns imago.core
  (:require
   [imago.config :as config]
   [imago.providers :refer [storage graph]]
   [imago.graph.api :as gapi]
   [imago.graph.vocab :refer :all]
   [imago.graph.model :as model]
   [imago.storage.api :as sapi]
   [imago.image :as image]
   [imago.utils :as utils]
   [thi.ng.validate.core :as v]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [ring.util.response :as resp]
   [ring.util.codec :as codec]
   [ring.middleware.defaults :as rd]
   [compojure.core :refer [defroutes context routes GET POST PUT DELETE]]
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
   [[:head
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
           (or (-> config/app :ui (config/mode) :override-config) "null") ";"))]]})

(defn current-user
  [req] (-> req :session :user))

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
        (resp/content-type type)
        (assoc :session (:session req)))))

(defn invalid-api-response
  [req]
  (-> (apply str
             "Only the following content types are supported: "
             (interpose ", " config/api-mime-types))
      (resp/response)
      (resp/status 406)
      (resp/content-type (:text config/mime-types))
      (assoc :session (:session req))))

(defn invalid-signature-response
  [req]
  (-> (resp/response "Request signature check failed")
      (resp/status 403)
      (resp/content-type (:text config/mime-types))
      (assoc :session (:session req))))

(defn missing-entity-response
  [req id] (api-response req {:reason (str "Unknown ID: " id)} 404))

(defn wrapped-api-handler
  [req params validate-id handler]
  (info "-------------------")
  (info :uri (:uri req))
  (info :cookies (:cookies req) :session (:session req))
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
    (invalid-api-response req)))

(defn collection-presets
  [coll-id]
  (->> (gapi/query-spec :collection-presets coll-id)
       (gapi/query graph)
       (vals)
       (map first)
       (map #(let [{:syms [?preset ?w ?h ?crop ?filter ?mime ?restrict]} %]
               [?preset ?w ?h ?crop ?filter ?mime ?restrict]))))

(defn image-version-producer
  "Takes a source image file to process and returns a fn accepting an
  Image instance and version preset spec. Produces resized version and
  adds it to storage. Returns updated Image instance w/ new version
  added."
  [src]
  (fn [img [pid w h crop flt mime restrict]]
    (let [version (model/make-imageversion {:id (str (:id img) "-" pid) :preset pid})
          tmp     (File/createTempFile "imago" nil)]
      (image/resize-image
       {:src src
        :dest tmp
        :type (subs (config/mime-ext mime) 1)
        :width w
        :height h
        :crop crop
        :filter flt})
      (sapi/put-object
       storage tmp
       (str (:id version) (config/mime-ext mime)) {})
      (.delete tmp)
      (update-in img [:versions] conj version))))

(defn handle-uploaded-images
  "Take a map of Image keys w/ file path vals and a seq of preset
  specs as returned by `collection-presets`. Produces resized versions
  for each, adds them to storage. Returns vector of Image
  instances (each populated w/ the various versions produced."
  [images presets]
  (->> images
       (reduce-kv
        (fn [acc img file]
          (->> presets
               ;; TODO restrict check/filter
               (reduce (image-version-producer file) img)
               (conj acc)))
        [])))

(def user-routes
  (routes
   (PUT "/" [:as req]
        (wrapped-api-handler
         req (:params req) :register
         (fn [req {:keys [fullname email username pass1 pass2]}]
           (let [user            (model/make-user
                                  {:type (:User imago)
                                   :name fullname
                                   :user-name username
                                   :email email
                                   :password pass1})
                 {:syms [?repo]} (->> (gapi/query-spec :get-repo)
                                      (gapi/query graph)
                                      (first))
                 base-rights     {:user (:id user) :context ?repo}
                 perms           #{(:canCreateColl imago) (:canViewRepo imago)}
                 rights          (map #(model/make-rightsstatement (assoc base-rights :perm %)) perms)
                 repo-rights     (map #(trio/triple ?repo (:accessRights dcterms) (:id %)) rights)
                 user'           (-> user (select-keys [:id :user-name :name]) (assoc :perms perms))
                 triples         (trio/triple-seq (concat [user] rights repo-rights))]
             (info :triples triples)
             (gapi/add-triples graph triples)
             (api-response req user' 201)))))
   (POST "/login" [:as req]
         (info "login attempt:" (:params req))
         (wrapped-api-handler
          req (:params req) :login
          (fn [req {:keys [user pass]}]
            (let [user' (->> (gapi/query-spec :login user pass model/salt)
                             (gapi/query graph)
                             (q/keywordize-result-vars)
                             (first))]
              (info :found-user user')
              (if user'
                (-> (api-response req user' 200)
                    (assoc :session {:user user'}))
                (api-response req "invalid login" 403))))))
   (POST "/logout" [:as req]
         (wrapped-api-handler
          req nil nil
          (fn [req _]
            (-> (api-response req (gapi/get-anon-user graph) 200)
                (assoc :session nil)))))
   (GET "/session" [:as req]
        (wrapped-api-handler
         req nil nil
         (fn [req _]
           (if-let [user (current-user req)]
             (do (info :user-session user)
                 (api-response req user 200))
             (api-response req "" 204)))))
   (GET "/:user/collections" [user :as req]
        (wrapped-api-handler
         req {:user user} :get-user-collections ;; TODO
         (fn [req _]
           (let [colls (->> (gapi/query-spec :get-user-collections user (:id (current-user req)))
                            (gapi/query graph)
                            (map (fn [[k v]] (first v))))]
             (info :user-colls colls)
             (api-response req colls 200)))))
   ))

(def media-routes
  (routes
   (GET "/images/:version" [version :as req]
        (let [img (->> (gapi/query-spec :media-item-version version)
                       (gapi/query graph)
                       (first))]
          (info :item img)
          (if img
            (-> (sapi/get-object-response storage (str version (config/mime-ext (img '?mime))))
                (resp/header "Content-Type" (img '?mime))
                (assoc :session (:session req)))
            (resp/not-found ""))))
   (GET "/collections/:coll-id" [coll-id :as req]
        (wrapped-api-handler
         req {:coll-id coll-id} :get-collection
         (fn [req params]
           (let [user (-> req current-user :id)
                 coll (->> (gapi/query-spec :describe-collection user coll-id)
                           (gapi/query graph)
                           (gapi/pack-triples))]
             (if (seq coll)
               (api-response req coll 200)
               (api-response req "Unauthorized" 403))))))
   (POST "/collections/:coll-id" [coll-id :as req]
         (info req)
         (wrapped-api-handler
          req (assoc (:params req) :user (current-user req)) :upload
          (fn [req params]
            (let [user-id (-> req current-user :id)
                  files   (filter :tempfile (vals (:params req)))
                  presets (collection-presets coll-id)
                  img-map (zipmap
                           (repeatedly #(model/make-stillimage {:coll-id coll-id :publisher user-id}))
                           (map :tempfile files))
                  _       (info :img-map img-map)
                  _       (info :presets presets)
                  images  (handle-uploaded-images img-map presets)]
              (->> images
                   (concat [[coll-id (:modified dcterms) (utils/timestamp)]])
                   (trio/triple-seq)
                   (gapi/add-triples graph))
              (api-response req (mapv :id images) 200)))))
   (PUT "/collections" [:as req]
        (wrapped-api-handler
         req (assoc (:params req) :user (current-user req)) :new-collection
         (fn [req {:keys [user title]}]
           (info :new-coll user title)
           (let [coll (model/make-collection-with-rights
                       {:creator (:id user)
                        :repo (ffirst (trio/select @(:g graph) nil (:type rdf) (:Repository imago)))
                        :presets [(-> config/version-presets :thumb-imago :id)]}
                       {:user (:id user) :perm (:canEditColl imago)})]
             (info :new-coll coll)
             (gapi/add-triples graph (trio/triple-seq coll))
             (api-response req (into {} coll) 201)))))))

(defroutes all-routes
  (GET "/" [:as req]
       (-> (:home page-cache)
           (seq)
           (html5)
           (resp/response)
           (resp/header "Content-Type" "text/html")
           (resp/charset "utf-8")
           (assoc-in [:session :user] (or (current-user req) (gapi/get-anon-user graph)))))
  (context "/users" [] user-routes)
  (context "/media" [] media-routes)
  (route/not-found "404"))

(def app
  (-> all-routes
      (rd/wrap-defaults (assoc-in rd/site-defaults [:security :anti-forgery] false))))
