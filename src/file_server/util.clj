(ns file-server.util)

(defn log [fn-name msg & args] 
  (println (str "[DEBUG][" fn-name "] " msg args)))

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