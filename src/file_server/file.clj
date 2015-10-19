(ns file-server.file
  (:require [file-server.store.leveldb-store :as leveldb-store]
            [clojure.java.io :as io])
  (:use [file-server.util]))

(def ^:private chunk-store (atom nil))
(def ^:private metadata-store (atom nil))

;; Utility methods to update the stores dynamically, useful for testing in REPL.
(defn set-chunk-store! [new-chunk-store]
  (reset! chunk-store new-chunk-store))

(defn set-metadata-store! [new-metadata-store]
  (reset! metadata-store new-metadata-store))

;; Public API
(defrecord Metadata [size content-type password retrieved manifest])

(defrecord Chunk [chunk-id start end size])

(defprotocol IFile
  (init! [this options])
  (add-chunk! [this chunk-id start end size input-stream])
  (get-chunk [this chunk-id])
  (has-chunk? [this chunk-id])
  (get-manifest [this])
  (get-property [this key])
  (set-property [this key val])
  (delete! [this]))

(defn ^:private convert-map-to-metadata [metadata-map]
  "Returns a Metadata record constructed from the given map, or nil if the map is nil."
  (if (nil? metadata-map) 
    nil 
    (map->Metadata metadata-map)))

(defn setup []
  "Initializes the backing stores for the File API."
  (let [chunk-store 
        (leveldb-store/setup-store! 
         "chunk" 
         {:write-wrappers [coerce-to-byte-array]})

        metadata-store 
        (leveldb-store/setup-store! 
         "metadata"
         {:key-decoder byte-streams/to-string
          :val-decoder byte-streams/to-string
          ;; Read and write wrappers are functions that transform the value of a read or write. The functions in the list are composed from left to right. E.g. [a b c] would result in a(b(c))
          ;; In this case, the sequence of types is:
          ;;     read() => :val-decoder(byte[]) -> String -> hashmap -> Metadata record
          ;;     Metadata record -> hashmap -> String -> :val-decoder(byte[]) => write()
          :read-wrappers [convert-map-to-metadata coerce-to-clojure]
          :write-wrappers [coerce-to-string coerce-record-to-map]})]

    (set-chunk-store! chunk-store)
    (set-metadata-store! metadata-store)))

;; Metadata related store functions

(defn ^:private initialize-metadata
  "Initialize the metadata for a new file."
  [id options]
  (let [record (->Metadata 
                (get options :size "*") 
                (get options :content-type "application/octet-stream") 
                (get options :file-password nil) 
                false
                {})]
    (.write! @metadata-store id record)))

(defn ^:private read-metadata
  "Queries the metadata-store and returns a Metadata record or nil."
  [id]
  (.read @metadata-store id))

(defn ^:private update-metadata
  "Updates the metadata record"
  [id key val]
  (let [record (read-metadata id)]
    (.write! @metadata-store id (assoc record key val))))

;; Chunk related store functions

(defn ^:private write-chunk 
  "Accepts an input-stream and persists it to the chunk-store under the chunk-id."
  [chunk-id input-stream]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (io/copy input-stream out)
      ;; Store the byte array into the chunk-store
    (.write! @chunk-store chunk-id out)))

(defn ^:private read-chunk
  "Reads a byte array from the chunk store corresponding to the given chunk-id."
  [chunk-id]
  (.read @chunk-store chunk-id))

(deftype File [id] IFile

         (init! [this options]
           (when (nil? (read-metadata id)) 
             (initialize-metadata id options)))

         (add-chunk! [this chunk-id start end size input-stream]
    ;; persist the chunk
           (write-chunk chunk-id input-stream)
    ;; update manifest
           (let [old-manifest (:manifest (read-metadata id))
                 updated-manifest (conj old-manifest {chunk-id (->Chunk chunk-id start end size)})]
             (update-metadata id :manifest updated-manifest)))

         (get-chunk [this chunk-id]
           (read-chunk chunk-id))

         (has-chunk? [this chunk-id]
           (not (nil? (get (.get-manifest this) chunk-id))))

         (get-manifest [this]
           (:manifest (read-metadata id)))

         (get-property [this key]
           (get (read-metadata id) key))

         (set-property [this key val]
           (update-metadata id key val))

         (delete! [this]
           (doall (map #(.delete! @chunk-store %) (keys (.get-manifest this))))
           (.delete! @metadata-store id)))

(defn file-exists? [id]
  (not (nil? (read-metadata id))))
