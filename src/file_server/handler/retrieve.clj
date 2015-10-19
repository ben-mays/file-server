(ns file-server.handler.retrieve
  (:use [org.httpkit.server]
        [clojure.string :only (split)]
        [file-server.util]
        [file-server.file.ghost-file :as file]))

(defn is-authorized? 
  "Returns true if the password supplied by the request matches the password stored with the file, or there is no password on the file."
  [request-password file-password]
  (or (nil? file-password) (= request-password file-password)))

(defn valid-file? 
  "Returns true if a file should be sent to the client."
  [request file]
  (let [headers (:headers request)
        request-password (get headers "file-password")]
    (and
      (false? (.get-property file :retrieved)) ;; has it already been retrieved? 
      (is-authorized? request-password (.get-property file :password))))) ;; is the user able to access the file?

(defn send-chunk 
  "Sends a chunk of a file into the channel, with the content-type and content-range headers set."
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
  "Sends a response to the client for each chunk in the file requested. Prior to sending a response, all chunks are ordered by the first byte index (:start) and then 
  sent sequentially. Support for HTTP/1.1 Chunked Encoding is not implemented, but could be added by simply sending a few extra characters in the :body field of the response."
  [request file]
  (with-channel request channel ;; open a 'channel' (basically a socket wrapper) and send each chunk.
    (loop [chunks (sort-by :start (vals (.get-manifest file)))]
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
  (let [file-id (-> request :params :id)]
    (if (file/file-exists? file-id)
      (let [file (->GhostFile file-id)]
        (when (valid-file? request file)
          (process-file-retrieve request file))))
    ;; if the file doesn't exist or the request wasn't valid, return 404.
    {:status 404}))
