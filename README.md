# clj-oa3-client

Component-based OpenADR 3 client framework built on [clj-oa3](https://github.com/grid-coordination/clj-oa3).

Provides `VenClient` and `BlClient` components with [Stuart Sierra's Component](https://github.com/stuartsierra/component) lifecycle management, a `NotificationChannel` protocol for MQTT and webhook notifications, and mDNS discovery for local VTNs.

## Features

- **Separate VEN/BL clients** — purpose-built components with role-specific capabilities
- **NotificationChannel protocol** — unified interface for MQTT and webhook notifications
- **mDNS discovery** — discover VTNs on the local network via `_openadr._tcp` service type
- **Component lifecycle** — clients, channels, and discoverer all implement `component/Lifecycle`
- **Config-driven construction** — specify URL, token (or OAuth2 credentials), and spec version
- **Automatic spec resolution** — just say `"3.1.0"` and the correct OpenAPI spec is found on the classpath
- **Full API delegation** — all `openadr3.api` functions available through the client
- **Both raw and coerced access** — HTTP responses or namespaced Clojure entities with `:openadr/raw` metadata

## Architecture

```
┌────────────────────────────────────────────────────┐
│  Your Application                                  │
│                                                    │
│  (base/programs my-ven)                            │
│  (ven/subscribe my-ven :mqtt topic-fn)             │
│  (ch/channel-messages mqtt-ch)                     │
├────────────────────────────────────────────────────┤
│  openadr3.client.ven       openadr3.client.bl      │
│    VenClient Component       BlClient Component    │
│    • VEN registration        • Admin access        │
│    • Channel management      • Full API            │
│    • Program caching                               │
│    • Notifier discovery                            │
│    • mDNS VTN discovery                            │
├────────────────────────────────────────────────────┤
│  openadr3.channel            openadr3.discovery    │
│    NotificationChannel         MdnsDiscoverer      │
│    MqttChannel (Component)     Component wrapping  │
│    WebhookChannel (Component)  clj-mdns            │
├────────────────────────────────────────────────────┤
│  openadr3.client.base                              │
│    Spec resolution, token fetch, API delegation    │
├────────────────────────────────────────────────────┤
│  clj-oa3 (openadr3.api + openadr3.entities)        │
│  Martian + Hato + OpenAPI spec                     │
└────────────────────────────────────────────────────┘
```

## Prerequisites

- [clj-oa3](../clj-oa3) must be checked out as a sibling directory (referenced via `:local/root`)
- [clj-mdns](../clj-mdns) must be checked out as a sibling directory (referenced via `:local/root`)
- The OpenADR 3 specification must be on the classpath (handled automatically via clj-oa3's `resources/` symlink)

## Quick Start

### VEN Client

```clojure
(require '[com.stuartsierra.component :as component]
         '[openadr3.client.ven :as ven]
         '[openadr3.client.base :as base])

;; Create and start a VEN client
(def my-ven
  (component/start
    (ven/ven-client {:url   "http://localhost:8080/openadr3/3.1.0"
                     :token "my-ven-token"})))

;; Register with the VTN
(ven/register! my-ven "my-ven-name")
(ven/ven-id my-ven)    ;=> "abc-123"

;; API access (via base namespace)
(base/programs my-ven)
(base/get-events my-ven)

;; Stop when done (auto-stops any channels)
(component/stop my-ven)
```

### BL Client

```clojure
(require '[openadr3.client.bl :as bl])

(def my-bl
  (component/start
    (bl/bl-client {:url   "http://localhost:8080/openadr3/3.1.0"
                   :token "my-bl-token"})))

(base/programs my-bl)
(base/create-program my-bl {:programName "My Program"})

(component/stop my-bl)
```

## Notification Channels

The `NotificationChannel` protocol provides a unified interface for MQTT and webhook notifications. Channels wrap the underlying transport (`openadr3.mqtt` / `openadr3.webhook`) behind a common API.

### MQTT via VenClient

```clojure
;; Add an MQTT channel (creates and starts it)
(ven/add-mqtt my-ven "tcp://broker:1883" {:client-id "my-ven"})

;; With broker authentication (e.g. Mosquitto dynsec)
(ven/add-mqtt my-ven "tcp://broker:1883" {:username "ven-123" :password "secret"})

;; Subscribe to VEN-scoped topics
(ven/subscribe my-ven :mqtt #(ven/get-mqtt-topics-ven %))

;; Check messages
(require '[openadr3.channel :as ch])
(def mqtt-ch (ven/get-channel my-ven :mqtt))
(ch/channel-messages mqtt-ch)
(ch/await-channel-messages mqtt-ch 3 5000)
(ch/clear-channel-messages! mqtt-ch)

;; Channels auto-stop on component/stop
```

### Webhook via VenClient

```clojure
;; Add a webhook channel (creates and starts HTTP server)
(ven/add-webhook my-ven {:port 0 :callback-host "192.168.1.50"})

;; Get the callback URL to register with the VTN
(def wh-ch (ven/get-channel my-ven :webhook))
(ch/callback-url wh-ch)
;; => "http://192.168.1.50:54321/notifications"

;; Check messages
(ch/channel-messages wh-ch)
(ch/await-channel-messages wh-ch 1 10000)
```

### Channels as Components

Channels implement both `NotificationChannel` and `component/Lifecycle`, so they
work standalone in a component system or managed by VenClient:

```clojure
;; As Components (component/start delegates to channel-start)
(def mqtt (component/start (ch/mqtt-channel "tcp://broker:1883")))
(ch/subscribe-topics mqtt ["programs/+" "events/+"])
(ch/channel-messages mqtt)
(component/stop mqtt)

;; With broker authentication
(def mqtt (component/start
            (ch/mqtt-channel "tcp://broker:1883"
                             {:username "ven-123" :password "secret"})))

;; Or via the protocol directly
(def wh (-> (ch/webhook-channel {:port 0 :callback-host "192.168.1.50"})
            ch/channel-start))
(ch/callback-url wh)
(ch/channel-stop wh)
```

## VEN-Specific Features

### VEN Registration

```clojure
;; Idempotent — finds existing VEN by name or creates a new one
(ven/register! my-ven "my-ven-name")
(ven/ven-id my-ven)    ;=> "abc-123"
(ven/ven-name my-ven)  ;=> "my-ven-name"
```

### Program ID Resolution (with caching)

```clojure
(ven/resolve-program-id my-ven "MyProgram")  ;=> "42" (API call)
(ven/resolve-program-id my-ven "MyProgram")  ;=> "42" (cache hit)
```

### Notifier Discovery

```clojure
(ven/discover-notifiers my-ven)     ;=> {:MQTT {:URIS [...]} ...}
(ven/vtn-supports-mqtt? my-ven)     ;=> true
(ven/mqtt-broker-urls my-ven)       ;=> ["tcp://broker:1883"]
```

### Event Polling

```clojure
(ven/poll-events my-ven)
(ven/poll-events my-ven {:program-id "42"})
```

### VEN-Scoped Topic Queries

These auto-use the registered VEN ID when called with one argument:

```clojure
(ven/get-mqtt-topics-ven my-ven)            ;; uses registered ven-id
(ven/get-mqtt-topics-ven my-ven "other-id") ;; explicit ID
(ven/get-mqtt-topics-ven-programs my-ven)
(ven/get-mqtt-topics-ven-events my-ven)
(ven/get-mqtt-topics-ven-resources my-ven)
```

## mDNS Discovery

The `MdnsDiscoverer` component discovers VTNs on the local network via mDNS
(service type `_openadr._tcp`). It implements `component/Lifecycle`.

### Standalone Discovery

```clojure
(require '[openadr3.discovery :as disc])

(def d (component/start (disc/mdns-discoverer)))
(disc/discover-vtns d)           ;; sync query, blocks 5s
(disc/discovered-services d)     ;; async — services trickle in
(disc/vtn-urls d)                ;; extract URLs from discovered services
(component/stop d)
```

### Wired into VenClient

When a VenClient has no `:url`, it resolves one from the injected discoverer on start:

```clojure
(def system
  (component/start-system
    {:discovery (disc/mdns-discoverer)
     :ven (component/using
            (ven/ven-client {:token "ven_token"})  ;; no URL needed
            [:discovery])}))

;; VenClient resolved URL from mDNS
(:url (:ven system))  ;=> "http://192.168.1.10:8080/openadr3/3.1.0"

(component/stop-system system)
```

### Options

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `:service-type` | string | `"_openadr._tcp.local."` | mDNS service type to discover |
| `:bind-address` | InetAddress | auto-detected LAN IP | Network interface to bind JmDNS to |

## Multiple Clients

Use `component/start-system` to manage multiple clients as a system:

```clojure
(def system
  (component/start-system
    {:ven (ven/ven-client {:url vtn-url :token "ven_token"})
     :bl  (bl/bl-client   {:url vtn-url :token "bl_token"})}))

(base/programs (:bl system))
(base/events (:ven system))

;; Introspection
(base/client-type (:ven system))  ;=> :ven
(base/scopes (:bl system))        ;=> #{"read_all" "read_bl" ...}
(base/authorized? (:ven system) :search-all-events)

(component/stop-system system)
```

## Client Options

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `:url` | string | yes* | — | VTN base URL (omit when using mDNS discovery) |
| `:token` | string | one of | — | Bearer auth token |
| `:client-id` | string | one of | — | OAuth2 client ID (used with `:client-secret`) |
| `:client-secret` | string | one of | — | OAuth2 client secret (used with `:client-id`) |
| `:spec-version` | string | no | `"3.1.0"` | OpenAPI spec version |

Either `:token` or both `:client-id` and `:client-secret` must be provided. When using client credentials, the token is fetched during `component/start` via the VTN's `/auth/server` endpoint.

Available spec versions: `"3.0.0"`, `"3.0.1"`, `"3.1.0"`, `"3.1.1"`

## API Reference

All `openadr3.api` functions are available through `openadr3.client.base`. The client is always the first argument.

### Raw CRUD (returns `{:status :body}`)

```clojure
;; Programs
(base/get-programs c)
(base/get-program-by-id c "program-id")
(base/search-programs c {:skip 0 :limit 10})
(base/create-program c {:programName "My Program"})
(base/update-program c "program-id" {:programName "Updated"})
(base/delete-program c "program-id")
(base/find-program-by-name c "My Program")

;; Events, VENs, Resources, Reports, Subscriptions — same pattern
```

### Coerced Entities

```clojure
(base/programs c)          ;; all programs
(base/program c "id")      ;; single program
(base/events c)
(base/vens c)
(base/reports c)
(base/subscriptions c)

;; Access raw data from any coerced entity
(-> (first (base/programs c)) meta :openadr/raw)
```

### Response Helpers

```clojure
(base/success? resp)  ;; true if 2xx
(base/body resp)      ;; extract :body
```

### MQTT Topics

```clojure
(base/get-mqtt-topics-programs c)
(base/get-mqtt-topics-program c "program-id")
(base/get-mqtt-topics-events c)
(base/get-mqtt-topics-reports c)
;; ... 12 topic endpoints total
```

### Introspection

```clojure
(base/all-routes c)                          ;; all 45 route keywords
(base/client-type c)                         ;=> :ven
(base/scopes c)                              ;=> #{"read_all" ...}
(base/endpoint-scopes c :search-all-events)  ;=> #{"read_all"}
(base/authorized? c :search-all-events)      ;=> truthy if allowed
```

## Namespace Guide

| Namespace | Purpose |
|-----------|---------|
| `openadr3.client.ven` | VenClient component, registration, channels, VEN operations |
| `openadr3.client.bl` | BlClient component |
| `openadr3.client.base` | Shared: spec resolution, token fetch, API delegation |
| `openadr3.channel` | NotificationChannel protocol, MqttChannel, WebhookChannel (all Components) |
| `openadr3.discovery` | MdnsDiscoverer Component for VTN discovery via mDNS |
| `openadr3.mqtt` | Low-level MQTT broker connection and subscription |
| `openadr3.webhook` | Low-level webhook HTTP server |
| `openadr3.net` | Network utilities (LAN IP detection, interface enumeration) |

## Component Lifecycle

Both `VenClient` and `BlClient` implement `component/Lifecycle`:

- **`start`** — Resolves the OpenAPI spec version, optionally fetches an OAuth2 token, bootstraps a Martian HTTP client. Idempotent.
- **`stop`** — Clears the `:martian` key. VenClient also auto-stops all notification channels. Idempotent.

```clojure
(def c (ven/ven-client {:url url :token token}))

(:martian c)                          ;=> nil (not started)
(def started (component/start c))
(:martian started)                    ;=> Martian client instance
(def stopped (component/stop started))
(:martian stopped)                    ;=> nil
```

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL running on port 7890
```

### Dev Helpers

The `dev/user.clj` namespace provides a system atom with convenience functions:

```clojure
(start!)                  ; Start system with VEN + BL clients
(stop!)                   ; Stop system

(client/programs (bl))    ; Use the BL client
(client/events (ven))     ; Use the VEN client

;; Or with a different VTN
(start! {:url "https://my-vtn.example.com/openadr3/3.1.0"})
```

## Dependencies

| Library | Purpose |
|---------|---------|
| [clj-oa3](../clj-oa3) | Pure OpenADR 3 client library (local dependency) |
| [clj-mdns](../clj-mdns) | mDNS service discovery (local dependency) |
| [Component](https://github.com/stuartsierra/component) | Lifecycle management |
| [machine_head](https://github.com/clojurewerkz/machine_head) | MQTT client (Paho wrapper) |
| [hato](https://github.com/gnarroway/hato) | HTTP client (OAuth2 token fetch) |
| [medley](https://github.com/weavejester/medley) | Utility functions |

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3](https://github.com/grid-coordination/clj-oa3) | Pure client library (dependency) |
| [clj-mdns](https://github.com/grid-coordination/clj-mdns) | mDNS discovery library (dependency) |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | OpenADR 3 integration tests |

## License

[MIT License](LICENSE) — Copyright (c) 2026 Clark Communications Corporation
