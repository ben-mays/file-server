(ns file-server.core
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics MetricFilter])
  (:require [file-server.file :as file]
            [file-server.handler.upload :as upload-handler]
            [file-server.handler.retrieve :as retrieve-handler])
  (:use [org.httpkit.server]
        [compojure.core]
        [compojure.route]
        [compojure.handler])
  (:gen-class))

(defroutes app-routes
  (GET "/file/:id" [] retrieve-handler/handle-file-retrieve)
  (PUT "/file/:id" [] upload-handler/handle-upload))

(defn -main
  "Setup the server and DB"
  [& args]
  (println "Starting server")
  (file/setup)
    ;; Set the max-body size to 2 MB to force us to chunk everything
  (run-server app-routes {:port 8081 :max-body 10485760 :thread 32}))
