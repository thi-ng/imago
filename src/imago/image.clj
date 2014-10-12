(ns imago.image
  (:require
   [clojure.java.io :as io]
   [taoensso.timbre :refer [info warn error]])
  (:import
   [net.coobird.thumbnailator Thumbnails]
   [net.coobird.thumbnailator.tasks.io FileImageSource]
   [javax.imageio ImageIO]
   [java.awt.image BufferedImage]))

;; https://code.google.com/p/thumbnailator/source/browse/src/net/coobird/thumbnailator/Thumbnails.java
;; https://code.google.com/p/thumbnailator/wiki/Examples

(defn resize-image
  [src dest dwidth dheight]
  (with-open [src (io/input-stream src)
              dest (io/output-stream dest)]
    (let [^BufferedImage img (ImageIO/read src)
          swidth (.getWidth img)
          sheight (.getHeight img)
          saspect (/ swidth sheight)
          daspect (/ dwidth dheight)
          [w h] (if (>= daspect saspect)
                  (let [h (int (/ swidth daspect))
                        w (int (* h daspect))]
                    [w h])
                  (let [w (int (* sheight daspect))
                        h (int (/ w daspect))]
                    [w h]))
          x (int (/ (- swidth w) 2.0))
          y (int (/ (- sheight h) 2.0))]
      (info "create image version:" src dest :size dwidth dheight)
      (.. (Thumbnails/of (into-array [img]))
          (sourceRegion x y w h)
          (size dwidth dheight)
          (outputFormat "jpg")
          (outputQuality 0.9)
          (toOutputStream dest)))))
