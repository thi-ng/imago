(ns imago.collection
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
  (fn [e]
    (.preventDefault e)
    (swap! local update-in [:files] dissoc id)
    (dom/remove! (dom/by-id id))
    (when (empty? (:files @local))
      (dom/show! (dom/by-id "drop-msg")))))

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
                 [:button.close {:type "button" :events [[:click (remove-file local id)]]} "\u00D7"]]
                [:div.col-xs-5 fname]
                [:div.col-xs-2 (ff/format [KB "KB"] fsize)]
                [:div.col-xs-4
                 [:div.progress
                  [:div.progress-bar {:style {:width "0%"}}]]]]
               (dom/by-id "dropzone"))
              (swap! local update-in [:files] assoc id file))
            (recur (inc i)))))
      (dom/remove-class! (dom/by-id "dropzone-wrapper") "dropzone-active")
      (debug @local))))

(defn show-template
  [state local]
  (->> (:app-root config/app)
       (dom/clear!)
       (dom/create-dom!
        [:div
         [:div#dropzone-wrapper.jumbotron
          {:events [[:dragenter (fn [e] (dom/add-class! (dom/by-id "dropzone-wrapper") "dropzone-active"))]
                    [:dragleave (fn [e] (dom/remove-class! (dom/by-id "dropzone-wrapper") "dropzone-active"))]
                    [:drop (add-files state local)]]}
          [:div#dropzone.container
           [:h2#drop-msg.text-center "Drop media files here"]]]
         [:button#bt-upload.btn.btn-primary.btn-lg
          {:events [[:click (upload-files state local)]]}
          "Upload media"]])))

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
        (show-template state local)
        (recur)))))
