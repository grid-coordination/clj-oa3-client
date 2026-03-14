(ns openadr3.client.base
  "Shared base functionality for OpenADR 3 clients.

  Contains spec resolution, OAuth2 token fetching, the Martian accessor,
  and all API delegation functions. These functions work with any client
  that has a :martian key (VenClient, BlClient, or legacy OA3Client)."
  (:require [openadr3.api :as api]
            [clojure.tools.logging :as log]
            [hato.client :as hc]))

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
;; OAuth2 token fetch (bypasses Martian — used before client creation)
;; ---------------------------------------------------------------------------

(defn fetch-token
  "Fetch an OAuth2 access token using client credentials grant.

  Discovers the token endpoint via GET {base-url}/auth/server, then
  POSTs to the token URL with the client credentials. Returns the
  access_token string.

  This uses hato directly (not Martian) because we need a token before
  we can create the authenticated Martian client."
  [base-url client-id client-secret]
  (let [base     (.replaceAll ^String base-url "/+$" "")
        http     (hc/build-http-client {:redirect-policy :normal})
        auth-resp (hc/get (str base "/auth/server")
                          {:http-client http :as :json})
        token-url (get-in auth-resp [:body :tokenURL])]
    (when-not token-url
      (throw (ex-info "No tokenURL in auth server response"
                      {:status (:status auth-resp)
                       :body   (:body auth-resp)})))
    (let [token-resp (hc/post token-url
                              {:http-client http
                               :form-params {:grant_type    "client_credentials"
                                             :client_id     client-id
                                             :client_secret client-secret}
                               :as :json})]
      (or (get-in token-resp [:body :access_token])
          (throw (ex-info "No access_token in token response"
                          {:status (:status token-resp)
                           :body   (:body token-resp)}))))))

;; ---------------------------------------------------------------------------
;; Martian accessor
;; ---------------------------------------------------------------------------

(defn martian
  "Get the underlying Martian client from a started client."
  [client]
  (or (:martian client)
      (throw (ex-info "Client not started. Call component/start first."
                      {:client-type (:client-type client)}))))

;; ---------------------------------------------------------------------------
;; Raw API access (returns HTTP responses)
;; ---------------------------------------------------------------------------

;; Programs
(defn get-programs     [c]        (api/get-programs (martian c)))
(defn get-program-by-id [c id]    (api/get-program-by-id (martian c) id))
(defn search-programs  [c q]      (api/search-programs (martian c) q))
(defn create-program   [c body]   (api/create-program (martian c) body))
(defn update-program   [c id body] (api/update-program (martian c) id body))
(defn delete-program   [c id]     (api/delete-program (martian c) id))
(defn find-program-by-name [c name] (api/find-program-by-name (martian c) name))

;; Events
(defn get-events       [c]        (api/get-events (martian c)))
(defn get-event-by-id  [c id]     (api/get-event-by-id (martian c) id))
(defn search-events    [c q]      (api/search-events (martian c) q))
(defn create-event     [c body]   (api/create-event (martian c) body))
(defn update-event     [c id body] (api/update-event (martian c) id body))
(defn delete-event     [c id]     (api/delete-event (martian c) id))

;; VENs
(defn get-vens         [c]        (api/get-vens (martian c)))
(defn search-vens      [c q]      (api/search-vens (martian c) q))
(defn get-ven-by-id    [c id]     (api/get-ven-by-id (martian c) id))
(defn create-ven       [c body]   (api/create-ven (martian c) body))
(defn update-ven       [c id body] (api/update-ven (martian c) id body))
(defn delete-ven       [c id]     (api/delete-ven (martian c) id))
(defn find-ven-by-name [c name]   (api/find-ven-by-name (martian c) name))

;; Resources
(defn search-ven-resources [c q]  (api/search-ven-resources (martian c) q))
(defn get-resource-by-id [c id]   (api/get-resource-by-id (martian c) id))
(defn create-resource  [c body]   (api/create-resource (martian c) body))
(defn update-resource  [c id body] (api/update-resource (martian c) id body))
(defn delete-resource  [c id]     (api/delete-resource (martian c) id))

;; Reports
(defn get-reports      [c]        (api/get-reports (martian c)))
(defn get-report-by-id [c id]     (api/get-report-by-id (martian c) id))
(defn search-reports   [c q]      (api/search-reports (martian c) q))
(defn create-report    [c body]   (api/create-report (martian c) body))
(defn update-report    [c id body] (api/update-report (martian c) id body))
(defn delete-report    [c id]     (api/delete-report (martian c) id))

;; Subscriptions
(defn get-subscriptions [c]       (api/get-subscriptions (martian c)))
(defn get-subscription-by-id [c id] (api/get-subscription-by-id (martian c) id))
(defn search-subscriptions [c q]  (api/search-subscriptions (martian c) q))
(defn create-subscription [c body] (api/create-subscription (martian c) body))
(defn update-subscription [c id body] (api/update-subscription (martian c) id body))
(defn delete-subscription [c id]  (api/delete-subscription (martian c) id))

;; Auth & notifiers
(defn get-notifiers    [c]        (api/get-notifiers (martian c)))
(defn get-auth-server  [c]        (api/get-auth-server (martian c)))
(defn get-token        [c client-id secret] (api/get-token (martian c) client-id secret))

;; MQTT topics (explicit ID required for VEN-scoped)
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

;; ---------------------------------------------------------------------------
;; Response helpers
;; ---------------------------------------------------------------------------

(defn success?         [resp] (api/success? resp))
(defn body             [resp] (api/body resp))

;; ---------------------------------------------------------------------------
;; Coerced entity access (returns namespaced entities with :openadr/raw metadata)
;; ---------------------------------------------------------------------------

(defn programs         [c]    (api/programs (martian c)))
(defn program          [c id] (api/program (martian c) id))
(defn events           [c]    (api/events (martian c)))
(defn event            [c id] (api/event (martian c) id))
(defn vens             [c]    (api/vens (martian c)))
(defn ven              [c id] (api/ven (martian c) id))
(defn reports          [c]    (api/reports (martian c)))
(defn report           [c id] (api/report (martian c) id))
(defn subscriptions    [c]    (api/subscriptions (martian c)))
(defn subscription     [c id] (api/subscription (martian c) id))

;; ---------------------------------------------------------------------------
;; Introspection
;; ---------------------------------------------------------------------------

(defn all-routes       [c]    (api/all-routes (martian c)))
(defn client-type      [c]    (api/client-type (martian c)))
(defn scopes           [c]    (api/scopes (martian c)))
(defn endpoint-scopes  [c ep] (api/endpoint-scopes (martian c) ep))
(defn authorized?      [c ep] (api/authorized? (scopes c) (endpoint-scopes c ep)))

;; ---------------------------------------------------------------------------
;; Topic extraction helper
;; ---------------------------------------------------------------------------

(defn extract-topics
  "Extract topic strings from a VTN MQTT topics response.
  Returns a vector of topic strings, or nil."
  [resp]
  (when (<= (:status resp) 299)
    (vals (:topics (:body resp)))))
