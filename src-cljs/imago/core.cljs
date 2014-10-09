(ns imago.core
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [om-bootstrap.grid :as g]
   [om-bootstrap.button :as bt]
   [om-bootstrap.nav :as nav]
   [om-bootstrap.random :as obr]
   [om-tools.dom :as otd :include-macros true]))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (dom/div
       nil
       (nav/navbar
        {:static-top? true :inverse? true :toggle-button true :toggle-nav-key "n" :brand "Imago\u2122" :on-toggle (constantly true)}
        (nav/nav
         {:collapsible? true}
         (nav/nav-item {:key 1 :href "#"} "Link")
         (nav/nav-item {:key 2 :href "#"} "Link")
         (bt/dropdown
          {:key 3, :title "Dropdown"}
          (bt/menu-item {:key 1} "Action")
          (bt/menu-item {:key 2} "Another action")
          (bt/menu-item {:key 3} "Something else here")
          (bt/menu-item {:divider? true})
          (bt/menu-item {:key 4} "Separated link"))))
       (g/grid
        {} (g/row {:xs 12}
                  (obr/page-header {} (:title data) " " (otd/small (:subtitle data)))))))))

(om/root
 widget
 {:title "Welcome to Imago"
  :subtitle "The media graph"}
 {:target (. js/document (getElementById "imago-app"))})
