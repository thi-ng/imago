(ns imago.storage.file
  (:require
   [imago.storage.api :as api]
   [clojure.java.io :as io]
   [taoensso.timbre :refer [info warn error]]))

(defn init-media-dir
  [path]
  (when-not (.exists (io/file path))
    (info "creating media storage directory:" path)
    (io/make-parents path ".imago")))

(defn storage-provider
  [conf]
  (let [base-path (-> conf :file :path)]
    (info "using file storage provider @" base-path)
    (init-media-dir base-path)
    (reify api/ImagoStorage
      (list-objects
        [_ re]
        (->> base-path
             (io/file)
             (file-seq)
             (filter #(re-matches re (.getPath %)))))
      (put-object
        [_ src dest opts]
        (let [dest (str base-path "/" dest)]
          (info "put object:" src ">>" dest)
          (io/make-parents dest)
          (with-open [i (io/input-stream src)
                      o (io/output-stream dest)]
            (io/copy i o)))))))
