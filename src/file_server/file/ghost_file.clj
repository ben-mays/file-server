(ns file-server.file.ghost-file
  "GhostFile is a basic implementation of the DistributedFile protocol and is `Store` agnostic.
   This implementation uses Metadata and Chunk record types for consistency when reading and writing. 

   In this implementation, there are only 2 stores used:
    * metadata-store
    * chunk-store

  Initially the intention was to move the manifest into a separate store as well, but the p99 for updating a manifest was ~3 ms, 
  whereas the latency distribution for `write-chunk` was had a long tail up to 250ms per chunk.

  The implementation is pretty straightforward, there are two stores. The chunk-store stores the actual byte array corresponding to the
  chunk. The metadata-store stores a map of properties for each file, including the manifest. 

  Unfortunately, Clojure deftype doesn't support doc strings.
  "

  (:require [file-server.store.leveldb-store :as leveldb-store]
            [clojure.java.io :as io])
  (:use [file-server.util]
        [file-server.interfaces :only (IDistributedFile)]))

(def ^:private chunk-store (atom nil))
(def ^:private metadata-store (atom nil))

;; A schema-based map-like structure that represents the metadata for a single GhostFile instance.
(defrecord Metadata 
  [size content-type password retrieved manifest])

;; A schema-based map-like structure that represents metadata about a single chunk for a file to be used as an entry in the manifest of a GhostFile instance.
(defrecord Chunk 
  [chunk-id start end])

;; Utility methods to update the stores dynamically, useful for testing in REPL.
(defn set-chunk-store! [new-chunk-store]
  (reset! chunk-store new-chunk-store))

(defn set-metadata-store! [new-metadata-store]
  (reset! metadata-store new-metadata-store))

(defn convert-map-to-metadata [metadata-map]
  "Returns a Metadata record constructed from the given map, or nil if the map is nil."
  (if (nil? metadata-map) 
    nil 
    (map->Metadata metadata-map)))

;; File helper functions
(defn ^:private initialize-metadata
  "Initialize a Metadata record and persist it."
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

(defn ^:private write-chunk 
  "Accepts an input-stream and persists it to the chunk-store under the chunk-id."
  [chunk-id input-stream]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (io/copy input-stream out)
    ;; Store the byte array into the chunk-store
    (.write! @chunk-store chunk-id out)))

(deftype GhostFile [id] IDistributedFile
  (init! [this options]
    (when (nil? (read-metadata id)) 
      (initialize-metadata id options)))

  ;; Persists a chunk and stores the chunk metadata into the manifest store.
  (add-chunk! [this chunk-id start end input-stream]
    ;; persist the chunk
    (write-chunk chunk-id input-stream)

    ;; update manifest
    (let [old-manifest (:manifest (read-metadata id))
          chunk-record (->Chunk chunk-id start end)
          updated-manifest (conj old-manifest {chunk-id chunk-record})]
      (.set-property this :manifest updated-manifest)))

  ;; Reads a byte array from the chunk store corresponding to the given chunk-id.
  (get-chunk [this chunk-id]
   (.read @chunk-store chunk-id))

  ;; Returns true if the chunk-id given is persisted in the manifest for the file.
  (has-chunk? [this chunk-id]
    (not (nil? (get (.get-manifest this) chunk-id))))

  ;; Returns the manifest for the file.
  (get-manifest [this]
    (:manifest (read-metadata id)))

  ;; Returns a key from the property map
  (get-property [this key]
    (get (read-metadata id) key))

  ;; Updates the property map for a File with the key and value given.
  (set-property [this key val]
    (let [record (read-metadata id)]
      (.write! @metadata-store id (assoc record key val))))

  ;; Deletes every chunk in the manifest and then deletes the metadata record.
  (delete! [this]
    (doall (map #(.delete! @chunk-store %) (keys (.get-manifest this))))
    (.delete! @metadata-store id)))

(defn file-exists? 
  "Returns true if there is no record for the given file-id."
  [id]
  (not (nil? (read-metadata id))))
