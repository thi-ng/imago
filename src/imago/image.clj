(ns imago.image
  (:require
   [clojure.java.io :as io]
   [taoensso.timbre :refer [info warn error]])
  (:import
   [net.coobird.thumbnailator Thumbnails]
   [net.coobird.thumbnailator.filters ImageFilter]
   [javax.imageio ImageIO]
   [java.awt.image BufferedImage]))

;; https://code.google.com/p/thumbnailator/source/browse/src/net/coobird/thumbnailator/Thumbnails.java
;; https://code.google.com/p/thumbnailator/wiki/Examples

(set! *unchecked-math* true)

(def bypass-filter
  (proxy [ImageFilter] [] (apply [^BufferedImage img] img)))

(def grayscale-filter
  (proxy [ImageFilter] []
    (apply [^BufferedImage img]
      (let [w (.getWidth img)
            h (.getHeight img)
            nump (* w h)
            pixels (int-array nump)]
        (.getRGB img 0 0 w h pixels 0 w)
        (loop [i 0]
          (when (< i nump)
            (let [col (int (aget ^ints pixels i))
                  r (* (bit-and (bit-shift-right col 16) 0xff) 77)
                  g (* (bit-and (bit-shift-right col 8) 0xff) 150)
                  b (* (bit-and col 0xff) 29)
                  l (bit-shift-right (+ (+ r g) b) 8)]
              (->> (bit-shift-left l 16)
                   (bit-or (bit-shift-left l 8))
                   (bit-or l)
                   (bit-or 0xff000000)
                   (aset ^ints pixels i))
              (recur (inc i)))))
        (.setRGB img 0 0 w h pixels 0 w)
        img))))

(def filters
  {"none"      bypass-filter
   "grayscale" grayscale-filter})

(defn compute-center-crop
  [swidth sheight width height]
  (let [saspect (/ swidth sheight)
        daspect (/ width height)
        [w h] (if (>= daspect saspect)
                (let [h (int (/ swidth daspect))
                      w (int (* h daspect))]
                  [w h])
                (let [w (int (* sheight daspect))
                      h (int (/ w daspect))]
                  [w h]))
        x (int (/ (- swidth w) 2.0))
        y (int (/ (- sheight h) 2.0))]
    [x y w h]))

(defn resize-image
  [{:keys [src dest type width height crop filter]}]
  (with-open [src (io/input-stream src)
              dest (io/output-stream dest)]
    (let [^BufferedImage img (ImageIO/read src)
          swidth (.getWidth img)
          sheight (.getHeight img)
          thumb (Thumbnails/of (into-array [img]))
          thumb (cond
                 crop               (let [[x y w h] (compute-center-crop
                                                     swidth sheight width height)]
                                      (.. thumb
                                          (sourceRegion x y w h)
                                          (size width height)))
                 (and width height) (.. thumb
                                        (sourceRegion 0 0 swidth sheight)
                                        (size width height))
                 width              (.size thumb width (int (* width (/ sheight swidth))))
                 height             (.size thumb (int (* height (/ swidth sheight))) height))
          thumb (.. thumb
                    (addFilter (filters filter bypass-filter))
                    (outputFormat type))
          thumb (if (= "jpg" type)
                  (.outputQuality thumb 0.9)
                  thumb)]
      (info "create image version:" src dest :size width height :type type :crop crop :filter filter)
      (.toOutputStream thumb dest))))
