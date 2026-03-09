(ns openadr3.client
  "Component-based OpenADR 3 client.

  Wraps the openadr3.api library with lifecycle management via
  Stuart Sierra's Component. Each OA3Client component represents
  a single authenticated connection to a VTN.

  Usage:
    (require '[openadr3.client :as client])

    ;; Create and start a VEN client
    (def c (-> (client/oa3-client {:type :ven
                                   :url \"http://localhost:8080/openadr3/3.1.0\"
                                   :token \"my-token\"})
               component/start))

    ;; Use it — all openadr3.api functions work via the :martian key
    (client/get-programs c)
    (client/programs c)       ;; coerced entities

    ;; Stop when done
    (component/stop c)"
  (:require [com.stuartsierra.component :as component]
            [openadr3.api :as api]
            [openadr3.entities :as entities]
            [clojure.tools.logging :as log]))

;; ---------------------------------------------------------------------------
;; Spec file resolution
;; ---------------------------------------------------------------------------

(def spec-versions
  "Map of OA3 spec version string to classpath resource path."
  {"3.0.0" "openadr3-specification/3.0.0/oadr3.0.0.yaml"
   "3.0.1" "openadr3-specification/3.0.1/openadr3.yaml"
   "3.1.0" "openadr3-specification/3.1.0/openadr3.yaml"
   "3.1.1" "openadr3-specification/3.1.1/openadr3.yaml"})

(def default-spec-version "3.1.0")

(defn spec-path
  "Resolve a spec version string to a classpath resource URL string.
  The spec is on the classpath via the clj-oa3 dependency.
  Throws if the version is unknown or the resource is not found."
  [version]
  (let [resource-path (or (get spec-versions version)
                          (throw (ex-info (str "Unknown OpenADR spec version: " version
                                               ". Known versions: " (keys spec-versions))
                                          {:version version
                                           :known (keys spec-versions)})))
        url (.getResource (clojure.lang.RT/baseLoader) resource-path)]
    (when-not url
      (throw (ex-info (str "OpenAPI spec not found on classpath: " resource-path
                           ". Ensure clj-oa3 is a dependency with the spec symlink in resources/.")
                      {:resource-path resource-path :version version})))
    (.getPath url)))

;; ---------------------------------------------------------------------------
;; Component
;; ---------------------------------------------------------------------------

(defrecord OA3Client [;; config (provided at construction)
                      client-type   ; :ven or :bl
                      url           ; VTN base URL
                      token         ; Bearer token
                      spec-version  ; e.g. "3.1.0"
                      ;; runtime (set on start)
                      martian       ; the Martian client instance
                      ]
  component/Lifecycle

  (start [this]
    (if martian
      (do (log/info "OA3Client already started" {:type client-type :url url})
          this)
      (let [spec-file (spec-path (or spec-version default-spec-version))
            create-fn (case client-type
                        :ven api/create-ven-client
                        :bl  api/create-bl-client)
            m (create-fn spec-file token url)]
        (log/info "OA3Client started" {:type client-type :url url
                                       :spec-version (or spec-version default-spec-version)})
        (assoc this :martian m))))

  (stop [this]
    (if martian
      (do (log/info "OA3Client stopped" {:type client-type :url url})
          (assoc this :martian nil))
      this)))

(defn oa3-client
  "Create an OA3Client component (not yet started).

  Options:
    :type         — :ven or :bl (required)
    :url          — VTN base URL (required)
    :token        — Bearer auth token (required)
    :spec-version — OpenAPI spec version, default \"3.1.0\"

  Call component/start to connect."
  [{:keys [type url token spec-version]}]
  {:pre [(#{:ven :bl} type)
         (string? url)
         (string? token)]}
  (map->OA3Client {:client-type  type
                   :url          url
                   :token        token
                   :spec-version (or spec-version default-spec-version)}))

;; ---------------------------------------------------------------------------
;; Convenience accessors — delegate to openadr3.api using :martian
;; ---------------------------------------------------------------------------

(defn martian
  "Get the underlying Martian client from a started OA3Client."
  [client]
  (or (:martian client)
      (throw (ex-info "OA3Client not started. Call component/start first."
                      {:client-type (:client-type client)}))))

;; Raw API access (returns HTTP responses)

(defn get-programs     [c]   (api/get-programs (martian c)))
(defn get-program-by-id [c id] (api/get-program-by-id (martian c) id))
(defn search-programs  [c q] (api/search-programs (martian c) q))
(defn create-program   [c body] (api/create-program (martian c) body))
(defn update-program   [c id body] (api/update-program (martian c) id body))
(defn delete-program   [c id] (api/delete-program (martian c) id))
(defn find-program-by-name [c name] (api/find-program-by-name (martian c) name))

(defn get-events       [c]   (api/get-events (martian c)))
(defn get-event-by-id  [c id] (api/get-event-by-id (martian c) id))
(defn search-events    [c q] (api/search-events (martian c) q))
(defn create-event     [c body] (api/create-event (martian c) body))
(defn update-event     [c id body] (api/update-event (martian c) id body))
(defn delete-event     [c id] (api/delete-event (martian c) id))

(defn get-vens         [c]   (api/get-vens (martian c)))
(defn get-ven-by-id    [c id] (api/get-ven-by-id (martian c) id))
(defn create-ven       [c body] (api/create-ven (martian c) body))
(defn update-ven       [c id body] (api/update-ven (martian c) id body))
(defn delete-ven       [c id] (api/delete-ven (martian c) id))
(defn find-ven-by-name [c name] (api/find-ven-by-name (martian c) name))

(defn search-ven-resources [c q] (api/search-ven-resources (martian c) q))
(defn get-resource-by-id [c id] (api/get-resource-by-id (martian c) id))
(defn create-resource  [c ven-id name] (api/create-resource (martian c) ven-id name))
(defn update-resource  [c id body] (api/update-resource (martian c) id body))
(defn delete-resource  [c id] (api/delete-resource (martian c) id))

(defn get-reports      [c]   (api/get-reports (martian c)))
(defn get-report-by-id [c id] (api/get-report-by-id (martian c) id))
(defn search-reports   [c q] (api/search-reports (martian c) q))
(defn create-report    [c body] (api/create-report (martian c) body))
(defn update-report    [c id body] (api/update-report (martian c) id body))
(defn delete-report    [c id] (api/delete-report (martian c) id))

(defn get-subscriptions [c] (api/get-subscriptions (martian c)))
(defn get-subscription-by-id [c id] (api/get-subscription-by-id (martian c) id))
(defn search-subscriptions [c q] (api/search-subscriptions (martian c) q))
(defn create-subscription [c body] (api/create-subscription (martian c) body))
(defn update-subscription [c id body] (api/update-subscription (martian c) id body))
(defn delete-subscription [c id] (api/delete-subscription (martian c) id))

(defn get-notifiers    [c]   (api/get-notifiers (martian c)))
(defn get-auth-server  [c]   (api/get-auth-server (martian c)))
(defn get-token        [c client-id secret] (api/get-token (martian c) client-id secret))

;; MQTT topics
(defn get-mqtt-topics-programs [c] (api/get-mqtt-topics-programs (martian c)))
(defn get-mqtt-topics-program [c id] (api/get-mqtt-topics-program (martian c) id))
(defn get-mqtt-topics-program-events [c id] (api/get-mqtt-topics-program-events (martian c) id))
(defn get-mqtt-topics-ven [c id] (api/get-mqtt-topics-ven (martian c) id))
(defn get-mqtt-topics-ven-programs [c id] (api/get-mqtt-topics-ven-programs (martian c) id))
(defn get-mqtt-topics-ven-events [c id] (api/get-mqtt-topics-ven-events (martian c) id))
(defn get-mqtt-topics-ven-resources [c id] (api/get-mqtt-topics-ven-resources (martian c) id))
(defn get-mqtt-topics-events [c] (api/get-mqtt-topics-events (martian c)))
(defn get-mqtt-topics-reports [c] (api/get-mqtt-topics-reports (martian c)))
(defn get-mqtt-topics-subscriptions [c] (api/get-mqtt-topics-subscriptions (martian c)))
(defn get-mqtt-topics-vens [c] (api/get-mqtt-topics-vens (martian c)))
(defn get-mqtt-topics-resources [c] (api/get-mqtt-topics-resources (martian c)))

;; Response helpers
(defn success?         [resp] (api/success? resp))
(defn body             [resp] (api/body resp))

;; Coerced entity access (returns namespaced entities with :openadr/raw metadata)

(defn programs         [c]   (api/programs (martian c)))
(defn program          [c id] (api/program (martian c) id))
(defn events           [c]   (api/events (martian c)))
(defn event            [c id] (api/event (martian c) id))
(defn vens             [c]   (api/vens (martian c)))
(defn ven              [c id] (api/ven (martian c) id))
(defn reports          [c]   (api/reports (martian c)))
(defn report           [c id] (api/report (martian c) id))
(defn subscriptions    [c]   (api/subscriptions (martian c)))
(defn subscription     [c id] (api/subscription (martian c) id))

;; Introspection
(defn all-routes       [c]   (api/all-routes (martian c)))
(defn client-type      [c]   (api/client-type (martian c)))
(defn scopes           [c]   (api/scopes (martian c)))
(defn endpoint-scopes  [c ep] (api/endpoint-scopes (martian c) ep))
(defn authorized?      [c ep] (api/authorized? (scopes c) (endpoint-scopes c ep)))
