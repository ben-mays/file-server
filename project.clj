(defproject file-server "0.1.0-SNAPSHOT"
  :description "Ghost Protocol"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"], 
                 [http-kit "2.1.18"],
                 [factual/clj-leveldb "0.1.1"],
                 [compojure "1.4.0"],
                 [ring-cors "0.1.7"],
                 [metrics-clojure "2.5.1"],
                 [metrics-clojure-graphite "2.5.1"],
                 [metrics-clojure-ring "2.5.1"]
                 [javax.servlet/servlet-api "2.5"]] ;; required for new versions of Ring that don't include the full dependency graph.
  :main ^:skip-aot file-server.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
