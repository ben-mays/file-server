(ns file-server.store
  (:require [file-server.store.chunk-store :as chunk-store]
            [file-server.store.metadata-store :as metadata-store]
            [clojure.java.io :as io])
  (:use [file-server.util]))

(def chunk-store (atom nil))
(def metadata-store (atom nil))

;; Utility methods to update the stores dynamically, useful for testing in REPL.
(defn set-chunk-store! [new-chunk-store]
  (reset! chunk-store new-chunk-store))

(defn set-metadata-store! [new-metadata-store]
  (reset! metadata-store new-metadata-store))

(defn setup []
  (let [chunk-store 
        (chunk-store/setup-store! 
         "chunk" 
         {:write-wrappers [coerce-to-byte-array]})

        metadata-store 
        (chunk-store/setup-store! 
         "metadata"
         {:key-decoder byte-streams/to-string
          :val-decoder byte-streams/to-string
          :read-wrappers [coerce-to-clojure]
          :write-wrappers [coerce-to-string]})]

    (set-chunk-store! chunk-store)
    (set-metadata-store! metadata-store)))

(defn metadata-record-nil?
  "Returns true if a metadata record exists for the file-id contained in the request data."
  [request]
  (let [file-id (-> request :data :filename)
        record (.read @metadata-store file-id)]
    (log "metadata-record-nil?" (nil? record))
    (nil? record)))

(defn write-chunk 
  "Accepts an input-stream and persists it to the chunk-store under the chunk-id."
  [chunk-id input-stream]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (io/copy input-stream out)
      ;; Store the byte array into levelstore
    (.write @chunk-store chunk-id out)
    (log "write-chunk" (str chunk-id " complete."))))

(defn add-chunk-to-manifest
  "Adds a chunk to the manifest for a file."
  [file-id chunk-id]
  (let [record (.read @metadata-store file-id)
        updated-manifest (conj (:manifest record) chunk-id)
        updated-record (assoc record :manifest updated-manifest)]
    (log "update-manifest" (str "Updated record for " file-id " to " updated-record "."))
    (.write @metadata-store file-id updated-record)))

(defn initialize-metadata
  "Initialize the metadata for a new file upload."
  [request]
  (let [file-id (-> request :data :filename)
        record {:content-type (-> request :data :content-type) 
                :password (-> request :data :file-password)
                :retrieved false
                :manifest []}]
    (log "write-metadata" (str "writing " record))
    (.write @metadata-store file-id record)))