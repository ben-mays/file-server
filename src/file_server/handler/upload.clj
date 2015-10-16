(ns file-server.handler.upload  
  (:require [file-server.store :as store])
  (:use [org.httpkit.server]
        [clojure.pprint]
        [clojure.string :only (split)]
        [file-server.util]
        ))

;; File Upload
(def filename-regex #"filename=[A-Za-z0-9.]+")
(def content-range-regex #"[0-9]+-[0-9]+")

(defn build-chunk-upload-response
  [status request]
  (let [basic-resp {:status status}
        start (-> request :data :content-start)
        end (-> request :data :content-end)
        length (-> request :data :content-length)
        range-header (str start "-" end "/" length)] ;; "$start-$end/$length"
        (merge basic-resp { :headers {"Range" range-header}
                            :body range-header})))

(defn parse-headers
  "Parses the raw request headers into a Clojure map and returns a modified request with the data, under the key :data."
  [request]
  (let [headers (:headers request)
        content-range-header (get headers "content-range")
        content-range (re-find content-range-regex content-range-header)
        content-disposition (get headers "content-disposition")
        data {:filename (last (split (re-find filename-regex content-disposition) #"=")) 
              :content-type (get headers "content-type")
              :content-start (Long/parseLong (first (split content-range #"-")))
              :content-end (Long/parseLong (last (split content-range #"-")))
              :content-length (Long/parseLong (last (split content-range-header #"/")))
              :file-password (get headers "file-password")}]
    (assoc request :data data)))



(defn handle-upload [request]
  (log "handle-file-upload" (str "new request " request))
  (let [request (parse-headers request)
        input-stream (-> request :body)
        file-id (-> request :data :filename)
        content-start (-> request :data :content-start)
        content-end (-> request :data :content-end)
        chunk-id (str file-id "-" content-start "-" content-end)
        ;; need to validate headers
        metadata-exists? (complement (store/metadata-record-nil? request))]

    ;; check if metadata already exists for this file, if not initialize it. 
    (when-not metadata-exists?
        (store/initialize-metadata request))
    
    (try
      (do
        ;; Store the chunk and update the manifest
        (store/write-chunk chunk-id input-stream)
        (store/add-chunk-to-manifest file-id chunk-id)
        ;; Return a 201 on the first chunk and a 200 on other successful requests.
        (let [status (if metadata-exists? 200 201)]
            (build-chunk-upload-response status request)))
      (catch Exception e
        (log "handle-upload" (str (.getMessage e)))
        (build-chunk-upload-response 500 request)))))