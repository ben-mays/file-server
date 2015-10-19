(ns file-server.core
  (:require [file-server.file.ghost-file :as file]
            [file-server.store.leveldb-store :as leveldb-store]
            [file-server.handler.upload :as upload-handler]
            [file-server.handler.retrieve :as retrieve-handler])
  (:use [org.httpkit.server]
        [compojure.core]
        [compojure.route]
        [compojure.handler]
        [file-server.util])
  (:gen-class))

(defroutes app-routes
  (GET "/file/:id" [] retrieve-handler/handle-file-retrieve)
  (PUT "/file/:id" [] upload-handler/handle-upload))

(defn setup-leveldb []
  "Initializes the backing stores for the File API."
  (let [chunk-store (leveldb-store/setup-store! 
         "chunk" 
         {:write-wrappers [coerce-to-byte-array]})

        metadata-store (leveldb-store/setup-store! 
         "metadata"
         {:key-decoder byte-streams/to-string
          :val-decoder byte-streams/to-string
          ;; Read and write wrappers are functions that transform the value of a read or write. The functions in the list are composed from left to right. 
          ;; E.g. [a b c] would result in a(b(c))
          ;; 
          ;; In this case, the sequence of types is:
          ;;  read() => :val-decoder(byte[]) -> String -> hashmap -> Metadata record
          ;;  Metadata record -> hashmap -> String -> :val-decoder(byte[]) => write()
          :read-wrappers [file/convert-map-to-metadata coerce-to-clojure]
          :write-wrappers [coerce-to-string coerce-record-to-map]})]

    (file/set-chunk-store! chunk-store)
    (file/set-metadata-store! metadata-store)))

(defn -main
  "Setup the server and DB"
  [& args]
  (println "Starting server")
  (setup-leveldb)
  (run-server app-routes {:port 8081 :max-body 10485760 :thread 32}))
