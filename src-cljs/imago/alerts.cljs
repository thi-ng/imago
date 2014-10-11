(ns imago.alerts
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require 
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :as async :refer [<! timeout]]))

(defn alert
  [msg parent]
  (let [alert (dom/create-dom! [:div.alert.alert-danger msg] nil)]
    (dom/insert! alert parent)
    (go
      (<! (timeout 3000))
      (dom/remove! alert))))
