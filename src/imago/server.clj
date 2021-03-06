(ns imago.server
  (:require
   [taoensso.timbre :refer [info warn]]
   [org.httpkit.server :as httpkit]
   [ring.middleware.reload :as reload]
   [compojure.handler :refer [site]]
   [imago.core]))

(taoensso.timbre/set-config!
 [:ns-blacklist] '[thi.ng.trio.query])

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)
    (info "server stopped")))

(defn -main [& args]
  (reset! server
          (httpkit/run-server
           (reload/wrap-reload #'imago.core/app)
           {:port 8080}))
  (info "started server @" 8080))
