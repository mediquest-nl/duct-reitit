(ns duct.router.reitit
  (:require [clojure.walk :as walk]
            [duct.core.resource]
            [integrant.core :as ig]
            [malli.util :as mu]
            [reitit.coercion.malli :as coercion.malli]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.malli]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]))

(def default-route-opts
  {:coercion (reitit.coercion.malli/create
              {;; set of keys to include in error messages
               :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
               ;; schema identity function (default: close all map schemas)
               :compile mu/closed-schema
               ;; strip-extra-keys (effects only predefined transformers)
               :strip-extra-keys true
               ;; add/set default values
               :default-values true
               ;; malli options
               :options nil})
   :middleware [muuntaja/format-response-middleware
                ;; !!!!coercion/coerce-exceptions-middleware
                coercion/coerce-request-middleware
                coercion/coerce-response-middleware

                ;; query-params & form-params
                parameters/parameters-middleware
                ;; content-negotiation
                muuntaja/format-negotiate-middleware
                ;; encoding response body
                muuntaja/format-response-middleware
                ;; exception handling
                ;; !!!! exception/exception-middleware
                ;; decoding request body
                muuntaja/format-request-middleware
                ;; coercing response bodys
                coercion/coerce-response-middleware
                ;; coercing request parameters
                coercion/coerce-request-middleware]})

(def default-default-handlers
  {:not-found          (ig/ref :duct.handler.static/not-found)
   :not-acceptable     (ig/ref :duct.handler.static/bad-request)
   :method-not-allowed (ig/ref :duct.handler.static/method-not-allowed)})

(defn- resolve-symbol [x]
  (if-let [var (and (symbol? x) (resolve x))]
    (var-get var)
    x))

(defmethod ig/prep-key :duct.router/reitit
  [_ {:keys [routes]
      ::ring/keys [opts default-handlers handlers]
      :or {handlers []}}]

  {:routes (walk/postwalk resolve-symbol routes)
   ::ring/opts (merge {:data default-route-opts} opts)
   ::ring/handlers handlers
   ::ring/default-handlers (merge default-default-handlers default-handlers)})

(def route-swagger
  ["/swagger.json"
   {:get {:no-doc true
          :swagger {:info {:title "my-api"
                           :description "with [malli](https://github.com/metosin/malli) and reitit-ring"}
                    :tags [{:name "files", :description "file api"}
                           {:name "math", :description "math api"}]}
          :handler (swagger/create-swagger-handler)}}])

(defmethod ig/init-key :duct.router/reitit
  [_ {:keys [routes]
      ::ring/keys [opts default-handlers handlers]}]
  (println :!!!!)
  (def routes routes)
  (def route-swagger route-swagger)
  (ring/ring-handler
   (ring/router (conj routes route-swagger) opts)
   (apply ring/routes
          (conj handlers
                (swagger-ui/create-swagger-ui-handler
                 {:path "/swagger"
                  :config {:validatorUrl nil
                           :operationsSorter "alpha"}})
                (ring/create-default-handler default-handlers)))))
