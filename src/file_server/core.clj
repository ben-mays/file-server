(ns file-server.core
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics MetricFilter])
  (:require [file-server.store :as store]
            [file-server.handler.upload :as upload-handler]
            [file-server.handler.retrieve :as retrieve-handler]
            [metrics.ring.instrument :refer [instrument]]
            [metrics.reporters.graphite :as graphite])
  (:use [org.httpkit.server]
        [compojure.core]
        [compojure.route]
        [compojure.handler])
  (:gen-class))

(def metrics (graphite/reporter {:host "localhost"
                                 :prefix "ghost"
                                 :rate-unit TimeUnit/SECONDS
                                 :duration-unit TimeUnit/MILLISECONDS
                                 :filter MetricFilter/ALL}))

(defroutes app-routes
  (GET "/file/:id" [] retrieve-handler/handle-file-retrieve)
  (PUT "/file" [] upload-handler/handle-upload))

(defn setup-middleware 
  "Wraps the generic api routes provided by compojure with a ring middleware for multipart handling."
  [] 
  (-> app-routes
      (instrument)))

(defn -main
  "Setup the server and DB"
  [& args]
  (println "Starting server")
  (graphite/start metrics 1)
  (store/setup)
  (let [routes (setup-middleware)]
    ;; Set the max-body size to 2 MB to force us to chunk everything
    (run-server routes {:port 8081 :max-body 10485760 :thread 32})))
