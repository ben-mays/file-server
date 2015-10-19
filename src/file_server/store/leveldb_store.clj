(ns file-server.store.leveldb-store
  (:require [clj-leveldb :as leveldb]))

(def ^:private db-prefix "/tmp/leveldb-")
(def ^:private stores (atom {}))

; Interface for LevelDBStore
(defprotocol ILevelDBStore
  (write! [this key val])
  (read [this keys])
  (delete! [this keys])
  (destroy! [this]))

(deftype LevelDBStore [database read-wrappers write-wrappers] ILevelDBStore
         (write! [this key val]
           (leveldb/put database key ((apply comp write-wrappers) val)))
         (read [this keys]
           ((apply comp read-wrappers) (leveldb/get database keys)))
         (delete! [this keys]
           (leveldb/delete database keys))
         (destroy! [this]
           (leveldb/destroy-db database)))

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
  (do
    (.destroy (get @stores store-name))
    (swap! stores (dissoc stores store-name))))

(defn get-store
  "Returns a store for the given store-name or nil if the store doesn't exist."
  [store-name]
  (get @stores store-name nil))
