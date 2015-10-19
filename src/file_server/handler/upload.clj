(ns file-server.handler.upload  
  (:use [org.httpkit.server]
        [clojure.pprint]
        [clojure.string :only (split)]
        [file-server.util]
        [file-server.file :as file]))

;; File Upload
(def filename-regex #"filename=[A-Za-z0-9.]+")
(def content-range-regex #"[0-9]+-[0-9]+")

(defn parse-headers
  "Parses the raw request headers into a Clojure map and returns a modified request with the data, under the key :data."
  [request]
  (println request)
  (let [headers (:headers request)
        content-range-header (get headers "content-range")
        content-range (re-find content-range-regex content-range-header)
        content-disposition (get headers "content-disposition")
        data {:filename (last (split (re-find filename-regex content-disposition) #"=")) 
              :content-type (get headers "content-type")
              :content-start (Long/parseLong (first (split content-range #"-")))
              :content-end (Long/parseLong (last (split content-range #"-")))
              :content-length (last (split content-range-header #"/"))
              :file-password (get headers "file-password")}]
    (assoc request :data data)))

(defn request-valid? 
  [request]
  true)

(defn handle-upload 
  [request]
  (let [request (parse-headers request)
        input-stream (-> request :body)
        file-id (-> request :params :id)
        {:keys [content-start content-end content-length]} (:data request)
        chunk-id (str file-id "-" content-start)
        ;; TODO: need to validate headers
        file (->File file-id)]

    ;; check if metadata already exists for this file, if not initialize it. 
    (when-not (file/file-exists? file-id)
      (.init! file (:data request)))

    (try
      (when (request-valid? request)
            ;; Check if chunk already exists first
        (when-not (.has-chunk? file chunk-id)
              ;; Store the chunk and update the manifest
          (.add-chunk! file chunk-id content-start content-end content-length input-stream))

            ;; Return a 201 on the first chunk and a 200 on other successful requests.
        {:status 200
         :body (format "%s-%s/%s" content-start content-end content-length)
         :headers {"Content-Range" (format "%s-%s/%s" content-start content-end content-length)}}
        :else {:status 400})
      (catch Exception e
        (log "handle-upload" (str (.getMessage e)))
        {:status 500}))))
