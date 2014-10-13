(defproject imago "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.18"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-devel "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [compojure "1.2.0"]
                 [hiccup "1.0.5"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [org.clojure/data.json "0.2.5"]
                 [com.taoensso/timbre "3.3.1"]
                 [environ "1.0.0"]
                 [camel-snake-kebab "0.1.5"]
                 [clj-time "0.6.0"]
                 [amazonica "0.2.27" :exclusions [joda-time]]
                 [net.coobird/thumbnailator "0.4.7"]

                 ;; cljs
                 [org.clojure/clojurescript "0.0-2322"]
                 [com.cognitect/transit-cljs "0.8.188"]
                 ;;[racehub/om-bootstrap "0.3.0" :exclusions [org.clojure/clojure]]
                 ;;[om "0.7.3"]
                 ;;[sablono "0.2.22"]

                 ;; cljx
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [thi.ng/trio "0.1.0-SNAPSHOT"]
                 [thi.ng/color "0.1.0-SNAPSHOT"]
                 [thi.ng/geom-core "0.3.0-SNAPSHOT"]
                 [thi.ng/geom-webgl "0.3.0-SNAPSHOT"]
                 [thi.ng/macromath "0.2.3"]
                 [thi.ng/validate "0.1.0-SNAPSHOT"]]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.3"]]}}

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/app.js"
                                   :optimizations :whitespace}}
                       {:id "release"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/app.min.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   ;;:preamble ["resources/public/lib/react.min.js"]
                                   ;;:externs ["js/externs/react.js"]
                                   }}]}
  :main imago.server)
