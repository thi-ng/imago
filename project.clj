(defproject imago "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2322"]
                 [racehub/om-bootstrap "0.3.0" :exclusions [org.clojure/clojure]]
                 [om "0.7.3"]
                 [ring "1.3.1"]
                 [http-kit "2.1.18"]
                 [compojure "1.1.8"]
                 [com.taoensso/timbre "3.3.1"]
                 [environ "1.0.0"]
                 [camel-snake-kebab "0.1.5"]
                 [simple-time "0.1.1"]
                 [amazonica "0.2.27" :exclusions [joda-time]]
                 [net.coobird/thumbnailator "0.4.7"]
                 [thi.ng/trio "0.1.0-SNAPSHOT"]
                 [thi.ng/macromath "0.2.3"]
                 [thi.ng/validate "0.1.0-SNAPSHOT"]]

  :main imago.server)
