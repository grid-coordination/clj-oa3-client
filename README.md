# clj-oa3-client

OpenADR 3 client application built on [clj-oa3](https://github.com/grid-coordination/clj-oa3). Provides mDNS VTN discovery and service management tooling.

## Features

- **mDNS discovery** of OpenADR 3 VTNs on the local network
- **tmux-based service management** for VTN-RI and callback services
- **Depends on clj-oa3** for all API operations

## Prerequisites

- [clj-oa3](../clj-oa3) must be checked out as a sibling directory
- [com.dcj/mdns](../../../mdns) must be available at its local path

## Quick Start

```clojure
(require '[openadr3.api :as api]
         '[openadr3.mdns :as mdns])

;; Start mDNS discovery
(mdns/discovery mdns/mdns-instance mdns/hosts)

;; Check discovered hosts
@mdns/hosts

;; Use the API client (from clj-oa3)
(def ven (api/create-ven-client "resources/openadr3-specification/3.1.0/openadr3.yaml"
                                 "my-token"
                                 "http://localhost:8080/openadr3/3.1.0"))
```

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL running on port 7890
```

### Service Management (from REPL)

```clojure
(run-vtn-ri)              ; Start VTN-RI in tmux
(kill-vtn-ri)             ; Stop VTN-RI
(run-vtn-callback-svc)    ; Start callback service in tmux
(kill-vtn-callback-svc)   ; Stop callback service
```

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3](https://github.com/grid-coordination/clj-oa3) | Pure client library (dependency) |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | Integration tests against VTN-RI |

## License

Copyright (c) 2026. All rights reserved.
