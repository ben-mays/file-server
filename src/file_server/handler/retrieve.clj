(ns file-server.handler.retrieve
  (:use [org.httpkit.server]
        [clojure.pprint]
        [clojure.string :only (split)]
        [file-server.util]
        [file-server.file :as file]))

;; File Retrieve 
(defn parse-headers
  "Parses the raw request headers into a Clojure map and returns a modified request with the data, under the key :data."
  [request]
  (let [file-id (-> request :params :file-id)
        headers (:headers request)
        data {:password (get headers "file-password")}]
    (assoc request :data data)))

(defn is-authorized? [user-password file-password]
  (println file-password user-password)  
  (or (nil? file-password) (= user-password file-password)))

(defn valid-file? 
  "Returns true if a file should be sent to the client."
  [request file]
  (println "RETRIEVED" (.get-property file :retrieved))
  (println "PASSWORD" (.get-property file :password))
  (and
   (false? (.get-property file :retrieved)) ;; has it been retrieved before? After retrieval we delete the file but there exists a race condition where multiple GETs for a single resource can succeed due to the storage layer.
   (is-authorized? (-> request :data :password) (.get-property file :password)))) ;; is the user able to access the file?

(defn send-chunk 
  [channel file chunk-record]
  (let [file-size (or (.get-property file :size) "*")
        content-type (or (.get-property file :content-type) "application/octet-stream")
        chunk-start (:start chunk-record)
        chunk-end (:end chunk-record)
        chunk-id (:chunk-id chunk-record)
        chunk (.get-chunk file chunk-id)
        response {:headers {"Content-Type" content-type 
                            "Content-Range" (format "%s-%s/%s" chunk-start chunk-end file-size)} 
                  :body (java.nio.ByteBuffer/wrap chunk)}]
    (send! channel response false)))

(defn send-file 
  "TODO: Support Chunked Encoding from HTTP/1.1"
  [request file]
  (println (.get-manifest file))
  (println (sort-by :start (vals (.get-manifest file))))
  (with-channel request channel ;; open a 'channel' (basically a socket wrapper) and send each chunk.
    (loop [chunks (sort-by :start (vals (.get-manifest file)))]
      (println "send-file" chunks (empty? chunks))
      (when-not (empty? chunks)
        (send-chunk channel file (first chunks))
        (recur (rest chunks))))
    (send! channel {:status 200})))

(defn process-file-retrieve [request file]
  (send-file request file)
  (.set-property file :retrieved true)
  (.delete! file))

(defn handle-file-retrieve
  [request]
  (println request)
  (let [request (parse-headers request)
        file-id (-> request :params :id)]
    (println (file/file-exists? file-id))
    (if (file/file-exists? file-id)
      (let [file (->File file-id)]
        (println "FILE" file)
        (when (valid-file? request file) ;; if the request is referencing a valid file, process the request and return HTTP 200.
          (process-file-retrieve request file))))
    ;; if we reach here either the file doesn't exist or the request wasn't invalid.
    {:status 404}))
