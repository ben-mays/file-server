(ns file-server.handler.upload  
  (:use [org.httpkit.server]
        [file-server.util]
        [file-server.file.ghost-file :as file]))

;; File Upload
(defn extract-file-data
  "Parses the raw request headers to extract metadata (file password, content-type, content-range) about the file transfer."
  [request]
  (let [headers (:headers request)
        data {:content-type (get headers "content-type")
              :file-password (get headers "file-password")}]
    (merge data (extract-content-header request))))

(defn request-valid? 
  "Returns true if the request contains the required fields to continue handling."
  [file request-data]
  ;; This might be confusing. (some predicate collection) will evaluate to nil if the predicate is not met.
  ;; The `nil?` predicate will return true if any of the required fields are nil.
  ;; So when (some nil? ...) returns nil, the correct headers are present. We then call nil? on the result to produce a boolean.
  (or (nil? (some nil?
        [(:content-start request-data)
        (:content-end request-data)]))
      (false? (.get-property file :retrieved))))

(defn handle-upload 
  [request]
  (let [file-id (-> request :params :id)
        file (->GhostFile file-id)
        request-data (extract-file-data request)
        {:keys [content-start content-end content-length content-type file-password]} request-data
        chunk-id (str file-id "-" content-start)]

    (println (format "[%s] Received upload request for chunk %s." file-id chunk-id))

    ;; check if metadata already exists for this file, if not initialize it. 
    (when-not (file/file-exists? file-id)
      (.init! file request-data))

    (try
      (if (request-valid? file request-data)
        (do
          ;; Check if chunk already exists first
          (when-not (.has-chunk? file chunk-id) 
            ;; Store the chunk and update the manifest
            (.add-chunk! file chunk-id content-start content-end (:body request)))
            (println (format "[%s] Wrote chunk %s." file-id chunk-id))

          ;; Return a 200 with the byte range uploaded in the Content-Range header and the body.
          {:status 200
           :body (format "%s-%s/%s" content-start content-end content-length)
           :headers {"Content-Range" (format "%s-%s/%s" content-start content-end content-length)}})
        {:status 400})
      (catch Exception e
        (log "handle-upload" (str (.getMessage e)))
        {:status 500}))))
