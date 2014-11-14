(ns imago.modal
  (:require
   [imago.config :as config]
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.domus.core :as dom]))

(defn modal-dialog
  [title body bt-label handler]
  (let [modal (:modal-root config/app)
        handler (fn [e] (.preventDefault e) (handler modal))]
    (->> modal
         (dom/clear!)
         (dom/create-dom!
          [:div.modal
           [:form
            [:div.modal-dialog.modal-sm
             [:div.modal-content
              [:div.modal-header
               [:h4 title]]
              [:div.modal-body body]
              [:div.modal-footer
               [:button.btn.btn-default
                {:type "button"
                 :events [[:click (fn [] (dom/clear! modal))]]}
                "Cancel"]
               [:input.btn.btn-primary
                {:type "submit"
                 :events [[:click handler]]
                 :value bt-label}]]]]]])
         (dom/show!))))
