(ns file-server.core
  (:import [java.nio.ByteBuffer]
           [java.util.concurrent TimeUnit]
           [com.codahale.metrics MetricFilter])
  (:require [clj-leveldb :as leveldb]
            [file-server.store :as store]
            [file-server.handler :as handler]
            [metrics.ring.instrument :refer [instrument]]
            [metrics.reporters.graphite :as graphite]
            )
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

(def cors-headers 
  { "Access-Control-Allow-Origin" ["*"]
    "Access-Control-Allow-Headers" ["Content-Type", "X-Requested-With", "Content-Range", "Content-Disposition"]
    "Access-Control-Allow-Methods" ["GET,PUT,POST,OPTIONS"]})

(defn all-cors
  "Allow requests from all origins"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response [:headers]
        merge cors-headers))))

(defn preflight [request] 
  (println request)
  {:status 200})

(defroutes app-routes
  ;; (GET "/file/:id" [] handler/handle-file-retrieve)
  (PUT "/file" [] handler/handle-upload)
  (OPTIONS "*" [] preflight))

(defn setup-middleware 
  "Wraps the generic api routes provided by compojure with a ring middleware for multipart handling."
  [] 
  (-> app-routes
      (all-cors)))

;; Functions to coerce types
(defn coerce-to-string [val]
  (condp = (type val)
    clojure.lang.PersistentVector (.toString val)
    clojure.lang.PersistentArrayMap (.toString val)
    val))

(defn coerce-to-byte-array [val]
  (condp = (type val)
    java.io.ByteArrayOutputStream (.toByteArray val)
    val))

(defn coerce-to-clojure [val]
  (condp = (type val)
    java.lang.String (read-string val)
    val))

(defn -main
  "Setup the server and DB"
  [& args]
  (println "Starting server")
  (graphite/start metrics 1)
  (def chunk-store 
    (store/setup-store! 
     "chunk" 
     {:write-wrappers [coerce-to-byte-array]}))

  (def metadata-store 
    (store/setup-store! 
     "metadata"
              {:key-decoder byte-streams/to-string
               :val-decoder byte-streams/to-string
               :read-wrappers [coerce-to-clojure]
               :write-wrappers [coerce-to-string]}))

  (handler/set-chunk-store! chunk-store)
  (handler/set-metadata-store! metadata-store)
  (let [routes (setup-middleware)]
    ;; Set the max-body size to 2 MB to force us to chunk everything
    (run-server routes {:port 8081 :max-body 10485760})))
