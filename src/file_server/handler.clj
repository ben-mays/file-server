(ns file-server.handler
  (:import [java.nio.ByteBuffer]
           [java.util.UUID])
  (:require [file-server.store :as store]
            [clojure.java.io :as io])
  (:use [org.httpkit.server]
        [clojure.pprint]
        [clojure.string :only (split)]))

(def chunk-store (atom nil))
(def metadata-store (atom nil))

;; Utility methods to update the stores dynamically, useful for testing in REPL.
(defn set-chunk-store! [new-chunk-store]
  (reset! chunk-store new-chunk-store))

(defn set-metadata-store! [new-metadata-store]
  (reset! metadata-store new-metadata-store))

;; File Upload
(defn uuid [] (str (java.util.UUID/randomUUID)))
(def filename-regex #"filename=[A-Za-z0-9.]+")
(def content-range-regex #"[0-9]+-[0-9]+")

(defn log [fn-name msg & args] 
  (println (str "[DEBUG][" fn-name "] " msg)))

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
              :content-start (Integer/parseInt (first (split content-range #"-")))
              :content-end (Integer/parseInt (last (split content-range #"-")))
              :content-length (Integer/parseInt (last (split content-range-header #"/")))
              :file-password (get headers "file-password")}]
    (assoc request :data data)))

(defn metadata-record-nil?
  "Returns true if a metadata record exists for the file-id contained in the request data."
  [request]
  (let [file-id (-> request :data :filename)
        record (.read @metadata-store file-id)]
    (log "metadata-record-nil?" (nil? record))
    (nil? record)))

(defn write-chunk 
  "Accepts an input-stream and persists it to the chunk-store under the chunk-id."
  [chunk-id input-stream]
  (with-open [out (java.io.ByteArrayOutputStream.)]
      (io/copy input-stream out)
      ;; Store the byte array into levelstore
      (.write @chunk-store chunk-id out)
      (log "write-chunk" (str chunk-id " complete."))))

(defn add-chunk-to-manifest
  "Adds a chunk to the manifest for a file."
  [file-id chunk-id]
  (let [record (.read @metadata-store file-id)
        updated-manifest (conj (:manifest record) chunk-id)
        updated-record (assoc record :manifest updated-manifest)]
    (log "update-manifest" (str "Updated record for " file-id " to " updated-record "."))
    (.write @metadata-store file-id updated-record)))

(defn initialize-metadata
  "Initialize the metadata for a new file upload."
  [request]
  (let [file-id (-> request :data :filename)
        record {:content-type (-> request :data :content-type) 
                :password (-> request :data :file-password)
                :retrieved false
                :manifest []}]
    (log "write-metadata" (str "writing " record))
    (.write @metadata-store file-id record)))

(defn handle-upload [request]
  (log "handle-file-upload" (str "new request " request))
  (let [request (parse-headers request)
        input-stream (-> request :body)
        file-id (-> request :data :filename)
        content-start (-> request :data :content-start)
        content-end (-> request :data :content-end)
        chunk-id (str file-id "-" content-start "-" content-end)
        ;; need to validate headers
        metadata-exists? (complement (metadata-record-nil? request))]

    ;; check if metadata already exists for this file, if not initialize it. 
    (when-not metadata-exists?
        (initialize-metadata request))
    
    (try
      (do
        ;; Store the chunk and update the manifest
        (write-chunk chunk-id input-stream)
        (add-chunk-to-manifest file-id chunk-id)
        ;; Return a 201 on the first chunk and a 200 on other successful requests.
        (let [status (if metadata-exists? 200 201)]
            (build-chunk-upload-response status request)))
      (catch Exception e
        (log "handle-file-upload" (str (.getMessage e)))
        (build-chunk-upload-response 500 request)))))

;; File Retrieve 

; (defn is-authorized? [request metadata]
;   (let [user-password (-> request :password)
;         file-password (-> metadata :password)]
;     (or (nil? file-password) (= user-password file-password))))

; (defn send-error-response [request metadata]
;   (if (nil? metadata)
;     (def error-code 404))
;   (if (true? (-> metadata :retrieved))
;     (def error-code 410))
;   (with-channel request channel
;     (send! channel
;            {:status error-code
;             :body "Something went wrong!"})))

; (defn send-file [request metadata]
;   (let [file-id (-> request :params :id)]
;        (with-channel request channel
;          (send! channel
;                 {:status 200
;                  :headers {"Content-Type" "application/octet-stream"}
;                  ;; Wrap our byte array into a ByteBuffer
;                  :body (java.nio.ByteBuffer/wrap (.read (store/get-store "data") file-id))}))))

; (defn update-retrieved
;   "Updates the metadata for a retrieved file and deletes the data"
;   [file-id record]
;   (.write (store/get-store "metadata") file-id (assoc record :retrieved true))
;   (.delete (store/get-store "data") file-id))

; (defn handle-file-retrieve
;   [request]
;   (let [file-id (-> request :params :id)
;         ;; Thanks to the R in REPL, we can easily recreate a Clojure datastructure from the toString output.
;         metadata (.read (store/get-store "metadata") file-id)]
;     (println file-id)
;     (println metadata)
;     (if (and
;          (false? (nil? metadata)) ;; does the metadata exist?
;          (false? (-> metadata :retrieved)) ;; has it been retrieved before?
;          (is-authorized? request metadata)) ;; is the user able to access the file?
;       (do
;         (send-file request metadata)
;         (update-retrieved file-id metadata))
;       (send-error-response request metadata))))
