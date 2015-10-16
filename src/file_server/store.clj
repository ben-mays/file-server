(ns file-server.store
  (:import [java.io.ByteArrayOutputStream])
  (:require [clj-leveldb :as leveldb]
            [metrics.gauges :refer [gauge-fn]]))

(def ^:private db-prefix "/tmp/leveldb-")
(def ^:private stores (atom {}))

(defn ^:private reporter-gauge [name db]
  (gauge-fn (str name "-db-size") #(leveldb/approximate-size db)))

; Interface for Store
(defprotocol IStore
  (write [this key val])
  (read [this key])
  (delete [this key])
  (get-db ^:private [this]))

;; TODO move the read/write wrappers to an option map. Add before/after for each operation.
(deftype LevelDBStore [database read-wrappers write-wrappers] IStore
  (write [this key val]
    (leveldb/put database key ((apply comp write-wrappers) val)))
  (read [this key]
    ((apply comp read-wrappers) (leveldb/get database key)))
  (delete [this key]
    (leveldb/delete database key))
  (get-db [this]
    database))


(defn setup-store!
  "Creates a new store with the given name, if none already exist. Returns a Store instance."
  [store-name & opts]
  (if (false? (contains? @stores store-name))
    (let [location (str db-prefix store-name)
          opts (into {} opts)
          db (leveldb/create-db location opts)
          store (->LevelDBStore db (:read-wrappers opts) (:write-wrappers opts))]

      ;; append the new store handler to the map of stores
      (swap! stores (assoc @stores store-name store))

      ;; setup a reporter for the store
      (reporter-gauge store-name db)
      store)
    (get @stores store-name)))

(defn destroy-store!
  "Destroys a store."
  [store-name]
  (do
    (leveldb/destroy-db (get-db (get stores store-name)))
    (swap! stores (dissoc stores store-name))))

(defn get-store
  "Returns a store for the given store-name or nil if the store doesn't exist."
  [store-name]
  (get @stores store-name))