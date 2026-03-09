# clj-oa3-client

Component-based OpenADR 3 client application built on [clj-oa3](https://github.com/grid-coordination/clj-oa3).

## Features

- **Component lifecycle** via [Stuart Sierra's Component](https://github.com/stuartsierra/component) — start/stop clients cleanly
- **Config-driven client construction** — specify type (VEN/BL), URL, token, and spec version
- **Automatic spec resolution** — just say `"3.1.0"` and the correct OpenAPI spec is found
- **Full API delegation** — all `openadr3.api` functions available through the client component
- **Both raw and coerced access** — HTTP responses or namespaced Clojure entities

## Prerequisites

- [clj-oa3](../clj-oa3) must be checked out as a sibling directory

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

;; Use it
(client/get-programs my-ven)     ;; raw HTTP response
(client/programs my-ven)         ;; coerced entities

;; Stop when done
(component/stop my-ven)
```

## Multiple Clients

```clojure
;; Start a system with multiple named clients
(def system
  (component/start-system
    {:ven (client/oa3-client {:type :ven :url vtn-url :token "ven_token"})
     :bl  (client/oa3-client {:type :bl  :url vtn-url :token "bl_token"})}))

;; Access by name
(client/programs (:bl system))
(client/events (:ven system))

;; Stop all
(component/stop-system system)
```

## Client Options

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `:type` | `:ven` or `:bl` | yes | — | Client role |
| `:url` | string | yes | — | VTN base URL |
| `:token` | string | yes | — | Bearer auth token |
| `:spec-version` | string | no | `"3.1.0"` | OpenAPI spec version |

Available spec versions: `"3.0.0"`, `"3.0.1"`, `"3.1.0"`, `"3.1.1"`

## API

All `openadr3.api` functions are available as client-namespaced wrappers:

```clojure
;; Raw (returns HTTP response maps)
(client/get-programs c)
(client/create-program c {:programName "My Program"})
(client/get-events c)
(client/create-ven c {:objectType "VEN_VEN_REQUEST" :venName "my-ven"})

;; Coerced (returns namespaced entities with :openadr/raw metadata)
(client/programs c)
(client/program c "some-id")
(client/events c)
(client/vens c)
(client/reports c)
(client/subscriptions c)

;; Introspection
(client/all-routes c)
(client/client-type c)    ;=> :ven
(client/scopes c)         ;=> #{"read_all" ...}
(client/authorized? c :search-all-events)
```

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL running on port 7890
```

### Dev helpers

```clojure
(start!)                  ; Start system with VEN + BL clients
(stop!)                   ; Stop system
(client/programs (bl))    ; Use the BL client
(client/events (ven))     ; Use the VEN client
```

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3](https://github.com/grid-coordination/clj-oa3) | Pure client library (dependency) |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | Integration tests against VTN-RI |

## License

Copyright (c) 2026. All rights reserved.
