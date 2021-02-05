(ns duct.router.reitit.handler
  (:require
   [integrant.core :as ig]
   [reitit.ring :as ring]))

(defmethod ig/init-key :duct.router.reitit.handler/redirect-trailing-slash [_ opts]
  (if (empty? opts)
    (ring/redirect-trailing-slash-handler)
    (ring/redirect-trailing-slash-handler opts)))
