(ns file-server.store
  (:require [file-server.store.chunk-store :as chunk-store]
            [file-server.store.metadata-store :as metadata-store]
            [clojure.java.io :as io]
            [metrics.timers :refer [timer deftimer time!]])
  (:use [file-server.util]))

(def chunk-store (atom nil))
(def metadata-store (atom nil))

(deftimer metadata-record-nil?-timer)
(deftimer add-chunk-to-manifest-timer)
(deftimer write-chunk-timer)
(deftimer initialize-metadata-timer)

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

(defn initialize-metadata
  "Initialize the metadata for a new file upload."
  [request]
  (let [file-id (-> request :data :filename)
        record {:content-type (-> request :data :content-type) 
                :password (-> request :data :file-password)
                :retrieved false
                :manifest []}]
    (.write @metadata-store file-id record)
    (log "write-metadata" (str "writing " record))))


(defn get-metadata-record
  [file-id]
   (.read @metadata-store file-id))

(defn metadata-record-nil?
  "Returns true if a metadata record exists for the file-id contained in the request data."
  [request]
  (let [file-id (-> request :data :filename)
        record (.read @metadata-store file-id)]
    (nil? record)))

(defn write-chunk 
  "Accepts an input-stream and persists it to the chunk-store under the chunk-id."
  [chunk-id input-stream]
  (time! write-chunk-timer
    (with-open [out (java.io.ByteArrayOutputStream.)]
      (io/copy input-stream out)
      ;; Store the byte array into the chunk-store
      (.write @chunk-store chunk-id out))))

(defn read-chunk
  ""
  [chunk-id]
  (.read @chunk-store chunk-id))

(defn add-chunk-to-manifest
  "Adds a chunk to the manifest for a file."
  [file-id chunk-id]
  (time! add-chunk-to-manifest-timer
    (let [record (.read @metadata-store file-id)
        updated-manifest (conj (:manifest record) chunk-id)
        updated-record (assoc record :manifest updated-manifest)]
    (.write @metadata-store file-id updated-record))))


(defn update-retrieved
  "Updates the metadata for a retrieved file and deletes the data"
  [file-id record]
  (.write @metadata-store file-id (assoc record :retrieved true))
  (.delete @chunk-store file-id))
