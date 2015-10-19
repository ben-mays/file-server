(ns file-server.util
  (:use [clojure.string :only (split)]))

(def content-range-regex #"[0-9]+-[0-9]+")

(defn log [fn-name msg & args] 
  (println (str "[DEBUG][" fn-name "] " msg args)))

(defn extract-content-header 
  "Parses the 'Content-Range' header out of a request map and returns a map with the keys :content-start, :content-end and :content-length populated. If "
  [request]
  (let [headers (:headers request)
        content-range-header (get headers "content-range")]
        (if (not (nil? content-range-header))
          (let [content-range (re-find content-range-regex content-range-header)]
            {:content-start (Long/parseLong (first (split content-range #"-")))
             :content-end (Long/parseLong (last (split content-range #"-")))
             :content-length (last (split content-range-header #"/"))}))))

;; Functions to coerce types
(defn coerce-record-to-map [record]
  (select-keys record (keys record)))

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