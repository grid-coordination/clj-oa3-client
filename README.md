# clj-oa3-client

Component-based OpenADR 3 client framework built on [clj-oa3](https://github.com/grid-coordination/clj-oa3).

Wraps the `openadr3.api` library with [Stuart Sierra's Component](https://github.com/stuartsierra/component) lifecycle management. Each `OA3Client` component represents a single authenticated connection to a VTN, with automatic OpenAPI spec resolution from the classpath.

## Features

- **Component lifecycle** — start/stop clients cleanly, compose into systems
- **Config-driven construction** — specify type (VEN/BL), URL, token, and spec version
- **Automatic spec resolution** — just say `"3.1.0"` and the correct OpenAPI spec is found on the classpath
- **Full API delegation** — all `openadr3.api` functions available through the client component
- **Both raw and coerced access** — HTTP responses or namespaced Clojure entities with `:openadr/raw` metadata

## Architecture

```
┌──────────────────────────────────────────────┐
│              Your Application                │
│                                              │
│  (client/programs my-ven)                    │
│  (client/get-events my-ven)                  │
│  (client/authorized? my-ven :search-vens)    │
├──────────────────────────────────────────────┤
│            openadr3.client                   │
│                                              │
│  OA3Client (Component)                       │
│    :client-type  :ven / :bl                  │
│    :url          VTN base URL                │
│    :token        Bearer token                │
│    :spec-version "3.1.0"                     │
│    :martian      ← set on component/start    │
│                                              │
│  Delegates to openadr3.api via :martian key  │
├──────────────────────────────────────────────┤
│  clj-oa3 (openadr3.api + openadr3.entities) │
│  Martian + Hato + OpenAPI spec               │
└──────────────────────────────────────────────┘
```

## Prerequisites

- [clj-oa3](../clj-oa3) must be checked out as a sibling directory (referenced via `:local/root`)
- The OpenADR 3 specification must be on the classpath (handled automatically via clj-oa3's `resources/` symlink)

## Quick Start

```clojure
(require '[com.stuartsierra.component :as component]
         '[openadr3.client :as client])

;; Create and start a VEN client
(def my-ven
  (component/start
    (client/oa3-client {:type  :ven
                        :url   "http://localhost:8080/openadr3/3.1.0"
                        :token "my-ven-token"})))

;; Raw API — returns HTTP response maps
(client/get-programs my-ven)
;; => {:status 200 :body [{:id "abc" :programName "MyProgram" ...}]}

;; Coerced entities — returns namespaced maps with :openadr/raw metadata
(client/programs my-ven)
;; => [#:openadr{:id "abc" :created #inst "..." :object-type :openadr.object-type/program}
;;     #:openadr.program{:name "MyProgram"}]

;; Stop when done
(component/stop my-ven)
```

## Multiple Clients

Use `component/start-system` to manage multiple clients as a system:

```clojure
(def system
  (component/start-system
    {:ven (client/oa3-client {:type :ven :url vtn-url :token "ven_token"})
     :bl  (client/oa3-client {:type :bl  :url vtn-url :token "bl_token"})}))

;; Access by name
(client/programs (:bl system))
(client/events (:ven system))

;; Introspection
(client/client-type (:ven system))  ;=> :ven
(client/scopes (:bl system))        ;=> #{"read_all" "read_bl" ...}
(client/authorized? (:ven system) :search-all-events)

;; Stop all
(component/stop-system system)
```

## Client Options

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `:type` | `:ven` or `:bl` | yes | — | Client role (determines OAuth2 scopes) |
| `:url` | string | yes | — | VTN base URL (e.g. `"http://localhost:8080/openadr3/3.1.0"`) |
| `:token` | string | yes | — | Bearer auth token |
| `:spec-version` | string | no | `"3.1.0"` | OpenAPI spec version |

Available spec versions: `"3.0.0"`, `"3.0.1"`, `"3.1.0"`, `"3.1.1"`

## API

All `openadr3.api` functions are available as client-namespaced wrappers. The client component is always the first argument.

### Raw CRUD (returns `{:status :body}`)

```clojure
;; Programs
(client/get-programs c)
(client/get-program-by-id c "program-id")
(client/search-programs c {:skip 0 :limit 10})
(client/create-program c {:programName "My Program"})
(client/update-program c "program-id" {:programName "Updated"})
(client/delete-program c "program-id")
(client/find-program-by-name c "My Program")

;; Events
(client/get-events c)
(client/get-event-by-id c "event-id")
(client/create-event c {:programID "p1" :intervals [...]})

;; VENs
(client/get-vens c)
(client/create-ven c {:objectType "VEN_VEN_REQUEST" :venName "my-ven"})
(client/find-ven-by-name c "my-ven")

;; Resources, Reports, Subscriptions — same pattern
```

### Coerced Entities (returns namespaced maps with `:openadr/raw` metadata)

```clojure
(client/programs c)          ;; all programs
(client/program c "id")      ;; single program
(client/events c)            ;; all events (with intervals, payloads, tick periods)
(client/event c "id")
(client/vens c)
(client/ven c "id")
(client/reports c)
(client/subscriptions c)

;; Access raw data from any coerced entity
(-> (first (client/programs c)) meta :openadr/raw)
```

### Response Helpers

```clojure
(client/success? resp)  ;; true if 2xx
(client/body resp)      ;; extract :body
```

### MQTT Topics

```clojure
(client/get-mqtt-topics-programs c)
(client/get-mqtt-topics-program c "program-id")
(client/get-mqtt-topics-ven c "ven-id")
;; ... and more (12 topic endpoints total)
```

### Introspection

```clojure
(client/all-routes c)                          ;; all 45 route keywords
(client/client-type c)                         ;=> :ven
(client/scopes c)                              ;=> #{"read_all" ...}
(client/endpoint-scopes c :search-all-events)  ;=> #{"read_all"}
(client/authorized? c :search-all-events)      ;=> truthy if allowed
```

## Component Lifecycle

The `OA3Client` record implements `component/Lifecycle`:

- **`start`** — Resolves the OpenAPI spec version to a classpath resource, bootstraps a Martian HTTP client via `api/create-ven-client` or `api/create-bl-client`. Idempotent (no-op if already started).
- **`stop`** — Clears the `:martian` key. Idempotent (no-op if already stopped).

```clojure
;; Lifecycle is idempotent
(def c (client/oa3-client {:type :ven :url url :token token}))

(:martian c)                          ;=> nil (not started)
(def started (component/start c))
(:martian started)                    ;=> Martian client instance
(def started2 (component/start started))
(identical? started started2)          ;=> true (no-op)
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
| [Component](https://github.com/stuartsierra/component) | Lifecycle management |
| [medley](https://github.com/weavejester/medley) | Utility functions |

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3](https://github.com/grid-coordination/clj-oa3) | Pure client library (dependency) |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | OpenADR 3 integration tests |

## License

Copyright (c) 2026. All rights reserved.
