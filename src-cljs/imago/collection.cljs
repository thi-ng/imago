(ns imago.collection
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [thi.ng.trio.core :as trio]
   [thi.ng.trio.query :as q]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [thi.ng.cljs.io :as io]
   [thi.ng.common.stringformat :as ff]
   [cljs.core.async :refer [<! timeout]]))

(defn KB [x] (int (/ x 1024)))

(defn upload-files
  [state local]
  (fn [e]
    (.preventDefault e)
    (let [{:keys [bus user]} @state
          fd (js/FormData.)
          fd (reduce-kv (fn [fd k v] (doto fd (.append k v))) fd (:files @local))]
      (io/request
       :uri     (config/api-route :collection (:id @local))
       :method  :post
       :edn?    true
       :data    fd
       :success (fn [status body]
                  (info :success-response status body)
                  (async/publish bus :upload-success (:body body)))
       :error   (fn [status body]
                  (warn :error-response status body)
                  (async/publish bus :upload-fail (:body body)))))))

(defn file-exists?
  [files name size]
  (some #(and (= name (.-name %)) (== size (.-size %))) (vals files)))

(defn remove-file
  [local id]
  (swap! local update-in [:files] dissoc id)
  (dom/remove! (dom/by-id id))
  (when (empty? (:files @local))
    (dom/show! (dom/by-id "drop-msg"))
    (dom/add-class! (dom/by-id "bt-upload") "disabled")))

(defn remove-all-files
  [state local])

(defn remove-file*
  [local id]
  (fn [e]
    (.preventDefault e)
    (remove-file local id)))

(defn add-files
  [state local]
  (fn [e]
    (.preventDefault e)
    (.stopPropagation e)
    (dom/hide! (dom/by-id "drop-msg"))
    (let [files (-> e (.-dataTransfer) (.-files))]
      (loop [i 0]
        (when (< i (.-length files))
          (let [file (aget files i)
                fname (.-name file)
                fsize (.-size file)
                id (str "file-" (utils/new-uuid))]
            (when-not (file-exists? (:files @local) fname fsize)
              (dom/create-dom!
               [:div.row {:id id}
                [:div.col-xs-1
                 [:button.close {:type "button" :events [[:click (remove-file* local id)]]} "\u00D7"]]
                [:div.col-xs-5 fname]
                [:div.col-xs-2 (ff/format [KB "KB"] fsize)]
                [:div.col-xs-4
                 [:div.progress
                  [:div.progress-bar {:style {:width "0%"}}]]]]
               (dom/by-id "dropzone"))
              (swap! local update-in [:files] assoc id file))
            (recur (inc i)))))
      (dom/remove-class! (dom/by-id "dropzone-wrapper") "dropzone-active")
      (dom/remove-class! (dom/by-id "bt-upload") "disabled")
      (debug @local))))

(defn show-template
  [state local]
  (->> (:app-root config/app)
       (dom/clear!)
       (dom/create-dom!
        [:div
         [:div.row
          [:div.col-xs-12
           [:h2 (:title @local)]]]
         (when (= (:owner @local) (-> @state :user :id))
           (list
            [:div#dropzone-wrapper.jumbotron
             {:events [[:dragenter (fn [e] (dom/add-class! (dom/by-id "dropzone-wrapper") "dropzone-active"))]
                       [:dragleave (fn [e] (dom/remove-class! (dom/by-id "dropzone-wrapper") "dropzone-active"))]
                       [:drop (add-files state local)]]}
             [:div#dropzone.container
              [:h2#drop-msg.text-center "Drop media files here"]]]
            [:div.row
             [:div.col-xs-12
              [:button#bt-upload.btn.btn-primary.btn-lg.disabled
               {:events [[:click (upload-files state local)]]}
               "Upload media"] " "
              [:button#bt-upload-cancel.btn.btn-default.btn-lg
               {:disabled "disabled"
                :events [[:click (remove-all-files state local)]]}
               "Cancel all"]]]))
         [:div.row
          [:div.col-xs-12
           [:h3 [:span.label.label-default (count (:items @local))] " items in collection..."]]]
         [:div.row
          (for [{:syms [?thumb ?xl]} (:items @local)]
            [:div.col-xs-4.col-md-2
             [:a.thumbnail {:href (str "/media/image/" ?xl)}
              [:img {:src (str "/media/image/" ?thumb)}]]])]
         ])))

(defn load-collection
  [state local]
  (io/request
   :uri     (config/api-route :collection (:id @local))
   :method  :get
   :edn?    true
   :success (fn [status body]
              (let [graph (trio/as-model (:body body))
                    title (-> graph (trio/select (:id @local) "dct:title" nil) (first) (peek))
                    owner (-> graph (trio/select (:id @local) "dct:creator" nil) (first) (peek))
                    items (q/query {:select :* :from graph
                                    :query [{:where '[[?img "dct:hasVersion" ?thumb]
                                                      [?img "dct:hasVersion" ?xl]
                                                      [?img "dct:dateSubmitted" ?time]
                                                      [?thumb "dct:references"
                                                       "617e6192-d1a3-4422-b3cc-d7fcfb782de5"]
                                                      [?xl "dct:references"
                                                       "fd9e54e5-3700-4736-ba32-a1bae45cf0b3"]]}]
                                    :order-desc '?time})]
                (swap!
                 local assoc
                 :title title
                 :owner owner
                 :items items
                 :graph graph)
                (show-template state local)))
   :error   (fn [status body]
              (warn :error-response status body)
              (async/publish (:bus @state) :load-coll-fail (:body body)))))

(defn init
  [bus]
  (let [init (async/subscribe bus :init-collection)
        stop! (fn [e] (.preventDefault e) (.stopPropagation e))
        local (atom {})]
    (debug :init-upload)
    (dom/add-listeners
     [[js/document :dragenter stop!]
      [js/document :dragover stop!]
      [js/document :drop stop!]])
    (go-loop []
      (let [[_ [state {:keys [id]}]] (<! init)]
        (swap! local assoc :id id :files {})
        (load-collection state local)
        (recur)))))
