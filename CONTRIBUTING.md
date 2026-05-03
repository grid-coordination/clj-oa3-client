# Contributing to clj-oa3-client

Thanks for your interest in contributing! This repo is a Clojure component-lifecycle wrapper around [clj-oa3](https://github.com/grid-coordination/clj-oa3) — it adds [Stuart Sierra Component](https://github.com/stuartsierra/component) lifecycle for VEN and BL clients, a `NotificationChannel` protocol unifying MQTT and webhook notifications, and an mDNS-based VTN discoverer. Datetime handling and OpenADR 3 schema concerns live upstream in `clj-oa3`; this library is concerned with wiring, lifecycle, and notification transport.

## How to contribute

### Discussions

Use [Discussions](https://github.com/grid-coordination/clj-oa3-client/discussions) for:

- Questions about how to use the library — VenClient/BlClient construction, channel selection (MQTT vs. webhook), mDNS discovery, registration flow, dev-system patterns
- API and design judgment calls — "should `NotificationChannel` model X?" / "is this the right shape for component wiring?"
- Cross-implementation or cross-repo coordination with [clj-oa3](https://github.com/grid-coordination/clj-oa3), [clj-mdns](https://github.com/grid-coordination/clj-mdns), or downstream consumers like [clj-price-server](https://github.com/grid-coordination/clj-price-server)
- Sharing what you're building on top of clj-oa3-client

Discussions are open-ended — a good place to think out loud or scope something before it becomes a concrete change. Aligned outcomes from a Discussion often turn into one or more Issues.

### Issues

Use [Issues](https://github.com/grid-coordination/clj-oa3-client/issues) for actionable changes:

- Bugs in component lifecycle (start/stop semantics, idempotency, channel auto-stop on VenClient stop)
- MQTT or webhook notification handling bugs (URI scheme normalization, payload coercion, double-encoded JSON, callback URL composition)
- mDNS discovery bugs (interface binding, service-type filtering, URL extraction from discovered services)
- Bugs in VEN-specific helpers (registration, program-ID resolution, notifier discovery, VEN-scoped topic queries)
- New channel transports or new component features when there is a concrete need
- Test failures or unexpected behavior with concrete repro steps
- Documentation errors, unclear explanations, or stale prose in `README.md` or namespace docstrings
- Discussion outcomes that have alignment and a clear scope

If a bug is really about wire format, schema shape, or datetime coercion, file it upstream in [clj-oa3](https://github.com/grid-coordination/clj-oa3/issues) — those concerns don't live here.

If you're not sure whether something is an Issue or a Discussion, start with a Discussion — we can convert it later.

### Pull requests

Pull requests are welcome.

- For small fixes (typos, broken links, single-test corrections, single-bug fixes), open a PR directly.
- For substantive changes (new channel transports, new component types, new lifecycle behavior, new dependencies), open a Discussion or Issue first so we can align on scope before you invest the effort.
- All changes pass `clojure -M:test` (Kaocha) and `clj-kondo --lint src test` cleanly.
- Match the existing tone and structure. The library wires clj-oa3 → component → notification channels as roughly orthogonal layers; patches that fit cleanly into one layer without leaking concerns across them are the easiest to land.
- One commit per logical change is fine; we don't require squash or any particular branch naming.

## Development

```bash
clojure -M:test                 # run the Kaocha unit test suite (offline, pure functions)
clojure -M:nrepl                # nREPL on the port written to .nrepl-port
clj-kondo --lint src test       # lint
```

Unit tests cover pure functions (URI normalization, callback URL composition, webhook payload parsing, mDNS service URL extraction). Integration testing against a live VTN happens in the sibling [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) repo — that's where end-to-end behavior of the component stack is verified against a running VTN.

## Code of conduct

Be respectful and constructive. We're a small project and appreciate everyone who takes the time to file an issue or send a PR.

## Important notice

This library is provided on an "as-is" basis. Updates and maintenance, including responses to issues filed on GitHub, will take place on an "as time and resources permit" basis. Library output (component lifecycle behavior, notification channel messages, discovered VTN URLs) is best-effort against the OpenADR 3 specification as published by the [OpenADR Alliance](https://www.openadr.org/) and consumed via [clj-oa3](https://github.com/grid-coordination/clj-oa3). This library is not authoritative for compliance certification — independent verification against the source specification and a certified VTN is recommended for any consumer using this client in a production setting.
