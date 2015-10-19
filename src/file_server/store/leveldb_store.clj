(ns file-server.store.leveldb-store
  "An implementation of the IStore protocol using LevelDB. Namespace contains useful utility functions for managing the database instances."
  (:require [clj-leveldb :as leveldb])
  (:use [file-server.interfaces :only (IStore)]))

(def ^:private db-prefix "/tmp/leveldb-")
(def ^:private stores (atom {}))

(deftype LevelDBStore [database read-wrappers write-wrappers] IStore
  (write! [this key val]
    (leveldb/put database key ((apply comp write-wrappers) val)))
  (read [this keys]
    ((apply comp read-wrappers) (leveldb/get database keys)))
  (delete! [this keys]
    (leveldb/delete database keys)))

(defn setup-store!
  "Creates a new LevelDBStore instance with the given name, or returns an existing instance of the store."
  [store-name & opts]
  (if (false? (contains? @stores store-name))
    (let [location (str db-prefix store-name)
          opts (into {} opts)
          db (leveldb/create-db location opts)
          store (->LevelDBStore db (:read-wrappers opts) (:write-wrappers opts))]

      ;; append the new store handler to the map of stores
      (swap! stores (assoc @stores store-name store))

      store)
    (get @stores store-name)))

(defn destroy-store!
  "Destroys a store."
  [store-name]
  (.destroy (get @stores store-name))
  (swap! stores (dissoc stores store-name)))

(defn get-store
  "Returns a store for the given store-name or nil if the store doesn't exist."
  [store-name]
  (get @stores store-name nil))
