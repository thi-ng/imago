(ns imago.image
  (:require
   [taoensso.timbre :refer [info warn error]])
  (:import
   [net.coobird.thumbnailator Thumbnails]
   [net.coobird.thumbnailator.tasks.io FileImageSource]
   [java.awt.image BufferedImage]))

(defn resize-image
  [src dest dwidth dheight]
  (let [^BufferedImage img (.read (FileImageSource. src))
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
        (outputQuality 0.9)
        (toFile dest))))
