(ns file-server.handler.retrieve
  (:require [file-server.store :as store])
  (:use [org.httpkit.server]
        [clojure.pprint]
        [clojure.string :only (split)]
        [file-server.util]))

;; File Retrieve 
(def content-range-regex #"[0-9]+-[0-9]+")

(defn parse-headers
  "Parses the raw request headers into a Clojure map and returns a modified request with the data, under the key :data."
  [request]
  (let [
  	file-id (-> request :params :file-id)
  	headers (:headers request)
        data {
              :file-id file-id
              :password (get headers "file-password")}]
    (assoc request :data data)))

(defn is-authorized? [user-password file-password]
  (println file-password user-password)  
  (or (nil? file-password) (= user-password file-password)))

(defn chunk-response [data]
  (str (format "%x" (alength data)) "\r\n" (apply str (map #(char (bit-and % 255)) data)) "\r\n"))

(defn send-file 
  "TODO: Support Chunked Encoding from HTTP/1.1"
  [request metadata]
  (with-channel request channel
    (loop [manifest (:manifest metadata)]
      (println manifest (empty? manifest))
      (when-not (empty? manifest)
        (let [chunk-id (first manifest)
              chunk (store/read-chunk chunk-id)
              chunk-start (nth (split chunk-id #"-") 1)
              chunk-end (last (split chunk-id #"-"))]
          (send! channel {:headers {"Content-Type" "video/mp4"} 
                          :body (java.nio.ByteBuffer/wrap chunk)}
                 false))
        (recur (rest manifest))))))

(defn handle-file-retrieve
  [request]
  (println request)
  (let [request (parse-headers request)
        file-id (-> request :params :id) 
        metadata (store/get-metadata-record file-id)
        manifest (:manifest metadata)]
    ;(println file-id)
    (println request)
    (println metadata)
    (println manifest)
    (println (false? (nil? metadata)))
    (println (false? (:retrieved metadata)))
    (println (is-authorized? (-> request :data :password) (:password metadata)))
    (for [i manifest]
      (println i))
    (if (and
         (false? (nil? metadata)) ;; does the metadata exist?
         (false? (:retrieved metadata)) ;; has it been retrieved before?
         (is-authorized? (-> request :data :password) (:password metadata))) ;; is the user able to access the file?
      (do
        (send-file request metadata)
        (store/update-retrieved file-id metadata))
      	(store/delete-file file-id)
      {:status 400})))
