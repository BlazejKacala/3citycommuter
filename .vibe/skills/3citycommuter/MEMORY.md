# Project Memory

## API Reference
Full ZTM Gdańsk API docs extracted from dane.pdf (v.2025-11-28):
`.vibe/skills/3citycommuter/api_reference.md`

Covers 26 endpoints: departures, GPS positions, stops, routes, timetables,
vehicles, ticket machines, GTFS, GTFS-RT, route changes, and more.

Key dynamic endpoints used in the app:
- Departures: `ckan2.multimediagdansk.pl/departures?stopId={stopId}`
- GPS: `ckan2.multimediagdansk.pl/gpsPositions?v=2`
- Shapes: `ckan2.multimediagdansk.pl/shapes?date=...&routeId=...&tripId=...`
- StopTimes: `ckan2.multimediagdansk.pl/stopTimes?date=...&routeId=...`
