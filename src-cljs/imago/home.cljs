(ns imago.home
  (:require-macros
   [cljs.core.async.macros :as asm :refer [go go-loop]])
  (:require
   [imago.config :as config]
   [thi.ng.cljs.async :as async]
   [thi.ng.cljs.log :refer [debug info warn]]
   [thi.ng.cljs.route :as route]
   [thi.ng.cljs.utils :as utils]
   [thi.ng.cljs.dom :as dom]
   [cljs.core.async :refer [<! timeout]]))

(defn init
  [bus]
  (debug :home-init))

