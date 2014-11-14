(ns imago.alerts
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require 
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.domus.core :as dom]
   [cljs.core.async :as async :refer [<! timeout]]))

(defn alert
  [type msg parent]
  (let [alert (dom/create-dom!
               [:div {:class (str "alert alert-" (name type))} msg]
               nil)]
    (dom/insert! alert parent)
    (go
      (<! (timeout 3000))
      (dom/remove! alert))))
