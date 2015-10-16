(ns file-server.handler.retrieve)

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
(defn handle-file-retrieve [request]
	(println request))
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
