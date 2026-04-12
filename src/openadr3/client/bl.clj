(ns openadr3.client.bl
  "BL (Business Logic) client component.

  A BL client represents a single authenticated business logic connection
  to a VTN with full administrative access.

  Usage:
    (require '[openadr3.client.bl :as bl])

    (def b (-> (bl/bl-client {:url \"http://vtn:8080/openadr3/3.1.0\"
                              :token \"bl_token\"})
               component/start))

    (base/get-programs b)
    (base/programs b)

    (component/stop b)"
  (:require [com.stuartsierra.component :as component]
            [openadr3.api :as api]
            [openadr3.client.base :as base]
            [com.brunobonacci.mulog :as mu]))

;; ---------------------------------------------------------------------------
;; Component
;; ---------------------------------------------------------------------------

(defrecord BlClient [;; config (provided at construction)
                     client-type     ; always :bl
                     url             ; VTN base URL
                     token           ; Bearer token (may be nil if using credentials)
                     client-id       ; OAuth2 client ID (optional)
                     client-secret   ; OAuth2 client secret (optional)
                     spec-version    ; e.g. "3.1.0"
                     user-agent      ; custom User-Agent suffix (optional)
                     ;; runtime (set on start)
                     http-client     ; shared Java HttpClient
                     martian         ; the Martian client instance
                     ;; mutable runtime state
                     state           ; atom
                     ]
  component/Lifecycle

  (start [this]
    (if martian
      (do (mu/log ::already-started :url url)
          this)
      (let [hc             (api/build-shared-http-client {})
            resolved-token (or token
                               (do (mu/log ::fetching-oauth2-token :client-id client-id)
                                   (base/fetch-token url client-id client-secret hc)))
            m              (api/create-bl-client resolved-token url
                                                 {:spec-version (or spec-version api/default-spec-version)
                                                  :http-client  hc
                                                  :user-agent   (base/compose-user-agent user-agent)})]
        (mu/log ::started :url url
                :spec-version (or spec-version api/default-spec-version))
        (assoc this :token resolved-token :http-client hc :martian m))))

  (stop [this]
    (when martian
      (mu/log ::stopped :url url))
    (assoc this :http-client nil :martian nil)))

(defmethod print-method BlClient [v ^java.io.Writer w]
  (.write w (str "#<BlClient " (:url v) ">")))

(defn bl-client
  "Create a BlClient component (not yet started).

  Options:
    :url           — VTN base URL (required)
    :token         — Bearer auth token (provide this OR client-id + client-secret)
    :client-id     — OAuth2 client ID (used with :client-secret)
    :client-secret — OAuth2 client secret (used with :client-id)
    :spec-version  — OpenAPI spec version, default \"3.1.0\"
    :user-agent    — custom User-Agent suffix for self-identification (optional)

  Call component/start to connect."
  [{:keys [url token client-id client-secret spec-version user-agent]}]
  {:pre [(string? url)
         (or (string? token)
             (and (string? client-id) (string? client-secret)))]}
  (map->BlClient {:client-type    :bl
                  :url            url
                  :token          token
                  :client-id      client-id
                  :client-secret  client-secret
                  :spec-version   (or spec-version api/default-spec-version)
                  :user-agent     user-agent
                  :state          (atom {})}))
