# ZTM Gdańsk API Reference
Source: dane.pdf v.2025-11-28 | Licencja: CCA

## Hosty
- `ckan.multimediagdansk.pl` – statyczne zasoby (raz/dobę)
- `ckan2.multimediagdansk.pl` – dynamiczne + shapes + stopTimes
- `files.cloudgdansk.pl` – pojazdy, biletomaty, BSK, herby, ZNT

Prefix CKAN: `https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/`

---

## DYNAMICZNE (~20s cache, TRISTAR)

### Departures from stop
```
GET https://ckan2.multimediagdansk.pl/departures?stopId={stopId}
```
`stopId` = stops.stopId

Response: `{ lastUpdate, departures[] }`

Departure fields (REALTIME):
```
id, delayInSeconds(s,ujemny=przyśp), estimatedTime(ISO-8601),
headsign, routeShortName, routeId, tripId, status="REALTIME",
theoreticalTime(ISO-8601), timestamp, trip, vehicleCode, vehicleId,
vehicleService("xxx-yy"), scheduledTripStartTime
```
Departure fields (SCHEDULED – brak estymy):
```
id, delayInSeconds=null, estimatedTime(ISO-8601 z rozkładu),
headsign, routeId, routeShortName, tripId, status="SCHEDULED",
theoreticalTime, timestamp, trip, vehicleCode=null, vehicleId=null,
vehicleService, scheduledTripStartTime
```

### Departures from ALL stops
```
GET http://ckan2.multimediagdansk.pl/departures
```
Struktura jak wyżej, dane pogrupowane po stopId.

### GPS Positions v2 (ZALECANA)
```
GET https://ckan2.multimediagdansk.pl/gpsPositions?v=2
```
Response: `{ lastUpdate(ISO-8601), vehicles[] }`

Vehicle fields:
```
generated(ISO-8601), routeShortName, tripId(int), routeId(int),
vehicleCode(string), vehicleService("xxx-yy"), vehicleId(int),
speed(km/h), delay(s, ujemny=przyśp), lat, lon,
gpsQuality(0=brak,1=słaby2D,2=2D,3=3D),
headsign, direction(0-315,co45°,0=N), scheduledTripStartTime(ISO-8601)
```
Uwaga: pojazdy ZKM Gdynia wykluczone (poza liniami wspólnymi). Brak sygnału = cache max 5 min.

### Display Messages (tablice)
```
GET http://ckan2.multimediagdansk.pl/displayMessages
```
Response: `{ lastUpdate, displaysMsg[] }`
```
displayCode(→displays.displayCode), displayName,
messagePart1, messagePart2,  // concat = treść
startDate("YYYY-MM-DD HH:MM:SS.D"),
endDate("9999-09-09..."=bezterminowy),
configurationDate, msgType(0=górny,1=dolny)
```

---

## STATYCZNE (raz/dobę)

### 2.1 Operators/Fleets (agency)
```
GET .../dff5f71f-0134-4ef3-8116-73c1a8e929a5/download/agency.json
```
```
lastUpdate, agency[]: {
  agencyId(PK), agencyName, agencyUrl, agencyTimezone, agencyLang,
  agencyEmail,
  topologyVersions[]: { versionNumber, startDate, endDate("9999-12-31"=aktywna) }
}
```

### 2.2 Routes/Lines
```
GET .../22313c56-5acf-41c7-a5fd-dc5dc72b3851/download/routes.json
```
Struktura: `{ "YYYY-MM-DD": { lastUpdate, routes[] } }`
```
routes[]: { routeId(PK), agencyId, routeShortName, routeLongName,
            activationDate, routeType(BUS|TRAM|FERRY|UNKNOWN) }
```
Uwaga: ZKM Gdynia zawsze ma UNKNOWN.

### 2.3 Stops (wszystkie Trójmiasto)
```
GET .../4c4025f0-01bf-41f7-a39f-d156d201b82b/download/stops.json
```
Struktura: `{ "YYYY-MM-DD": { lastUpdate, stops[] } }`
```
stops[]: {
  stopId(PK, używany w departures), stopCode, stopName(ZTM-only),
  stopShortname, stopDesc(TRISTAR), subName(nr słupka w ramach przystanku),
  date, stopLat, stopLon,  // WGS84/EPSG:3857
  zoneId(ZTM-only), zoneName,
  wheelchariBoarding(0/1), virtual(0/1), nonpassenger(0/1),
  depot(0/1), ticketZoneBorder(0/1), onDemand(0/1), activationDate
}
```

### 2.4 Stops – ZTM Gdańsk only
```
GET .../d3e96eb6-25ad-4d6c-8651-b1eb39155945/download/stopsingdansk.json
```
Identyczna struktura jak 2.3, tylko przystanki ZTM Gdańsk.

### 2.5 Stop Displays (tablice przystankowe)
```
GET .../ee910ad8-8ffa-4e24-8ef9-d5a335b07ccb/download/displays.json
```
```
{ lastUpdate, displays[]: {
  displayCode(PK), name, idStop1, idStop2, idStop3, idStop4  // stopId z 2.3, 0=brak
} }
```

### 2.6 Trips/Variants
```
GET .../b15bb11c-7e06-4685-964e-3db7775f912f/download/trips.json
```
Struktura: `{ "YYYY-MM-DD": { lastUpdate, trips[] } }`
```
trips[]: {
  id("R{routeId}T{tripId}"), routeId, tripId(PK w ramach linii),
  tripHeadsign, tripShortName(=tripId),
  directionId(1=tam,2=powrót), activationDate,
  type(MAIN|SIDE|NON_PASSENGER|UNKNOWN)
}
```

### 2.7 Validity periods
```
GET .../f84afb16-a271-4dce-80a5-3ff20dfd4f97/download/expeditiondata.json
```
```
{ lastUpdate, expeditionData[]: {
  startDate, endDate, routeId, tripId,
  technicalTrip(0/1), mainRoute(0/1)
} }
```

### 2.8 Date ranges
```
GET .../9c3d6fed-5394-4ef1-b2c6-c87169991 49c/download/stoptimesspan.json
```
```
{ lastUpdate, expeditionData[]: { agencyId, startDate, endDate } }
```

### 2.9 Stops in Trips
```
GET .../3115d29d-b763-4af5-93f6-763b835967d6/download/stopsintrips.json
```
Struktura: `{ "YYYY-MM-DD": { lastUpdate, stopsInTrip[] } }`
```
stopsInTrip[]: {
  routeId, tripId, stopId, stopSequence, agencyId, topologyVersionId,
  passenger(true|false|null),  // null = ZKM Gdynia
  tripActivationDate, stopActivationDate
}
```

### 2.10 Route shapes
```
GET https://ckan2.multimediagdansk.pl/shapes?date={YYYY-MM-DD}&routeId={routeId}&tripId={tripId}
```
Response: GeoJSON LineString
```
{ type:"LineString", coordinates:[[lon,lat],...],
  properties:{ date, routeId, tripId } }
```
Indeks URL-i kształtów: `https://ckan.multimediagdansk.pl/dataset/tristar/resource/da610d2a-7f54-44d1-b409-c1a7bdb4d3a4`

### 2.15 Timetable / Schedule
```
GET http://ckan2.multimediagdansk.pl/stopTimes?date={YYYY-MM-DD}&routeId={routeId}
```
```
{ lastUpdate, stopTimes[]: {
  routeId, tripId, agencyId, topologyVersionId,
  arrivalTime("YYYY-MM-DD"+"T"+"HH:MM:SS", 1899-12-30=ten dzień, 1899-12-31=następny),
  departureTime(j/w), stopId, stopSequence, date,
  variantId(ZTM-only), noteSymbol, noteDescription, busServiceName("xxx-yy"),
  order, passenger(bool,ZTM-only), nonpassenger(bit), ticketZoneBorder(bit),
  onDemand(bit), virtual(bit), islupek(int,ZTM-only),
  wheelchairAccessible(bit,ZTM-only), stopShortName
} }
```
Indeks: `.../.../a023ceb0-8085-45f6-8261-02e6fcba7971/download/stoptimes.json`

### 2.16 GTFS Schedule (zip)
```
GET .../30e783e4-2bec-4a7d-bb22-ee3e3b26ca96/download/gtfsgoogle.zip
```
Pliki: agency.txt, calendar_dates.txt, feed_info.txt, routes.txt,
       shapes.txt, stop_times.txt, stops.txt, trips.txt

routeType w GTFS: tramwaj=900, autobus=700, tramwaj wodny=1200

trip_id format: `<trip>_<variantId>_<busServiceName>`
→ variantId == Route w GPSPositions
→ busServiceName == VehicleService w GPSPositions

### 2.17 GTFS-RT (protobuf)
```
GET .../976e1fd1-73d9-4237-b6ba-3c06004d1105/download/linki.json
```
Zawiera URL-e do: Trip updates, Vehicle positions (format protobuf)

---

## INNE ZASOBY

### 2.18 Ticket machines (biletomaty)
```
GET https://files.cloudgdansk.pl/d/otwarte-dane/ztm/biletomaty.json?v=1
```
```
results[]: { number, address, district, description,
             latitude, longitude, damaged(bool),
             availableTickets, paymentMethods }
```

### 2.19 Common stop poles GDA+GDY
```
GET .../f8a5bedb-7925-40c9-8d66-dbbc830939b1/download/przystanki_wspolnegda_gdy.json
```
```
[]: { publicCodeGdansk, stopIdGdansk, publicCodeGdynia, stopIdGdynia,
      stopName, stopCode }
```

### 2.20 Current transport situation (BSK)
```
GET https://files.cloudgdansk.pl/d/otwarte-dane/ztm/bsk.json?v=2
```
```
results[]: { lineNumbers[], title, summary, content,
             url, publishFrom, publishTo }
```

### 2.21 Static data update timestamps
```
GET .../78b5cd75-1884-4878-8704-7a622d84d709/download/summary.html
```

### 2.22 Vehicle database
```
GET https://files.cloudgdansk.pl/d/otwarte-dane/ztm/baza-pojazdow.json?v=2
```
```
results[]: {
  vehicleCode(=VehicleCode w GPS), carrier,
  transportationType("autobus"|"tramwaj"),
  vehicleCharacteristics(Minibus|Midibus|Standardowy|Wielkopojemny|Przegubowy),
  bidirectional(bool), historicVehicle(bool),
  length(m), brand, model, productionYear,
  seats(int), standingPlaces(int),
  airConditioning(bool), monitoring(bool), internalMonitor(bool),
  floorHeight("niskopodłogowy"|"częściowo niskopodłogowy"|"wysokopodłogowy"),
  kneelingMechanism(bool), wheelchairsRamp(bool),
  usb(bool), voiceAnnouncements(bool), aed(bool),
  bikeHolders(int), ticketMachine(bool),
  patron, url, passengersDoors(int), photo
}
```
Zdjęcia: `https://files.cloudgdansk.pl/f/otwarte-dane/ztm/baza-pojazdow/{photo}.jpg`

### 2.23 Line categories
```
GET .../8b5175e6-7621-4149-a9f8-a29696c73d8d/download/kategorie.json
```
```
results[]: { category(string), routeIdRange:{ startValue, endValue } }
```

### 2.24 Municipal crests (herby)
```
GET https://files.cloudgdansk.pl/d/otwarte-dane/ztm/herby.json?v=1
```
```
results[]: { zoneId(=stops.zoneId), zoneName, url }
```

### 2.25 Route changes (ZNT)
```
GET https://files.cloudgdansk.pl/d/otwarte-dane/ztm/znt.json
```
```
results[]: { lineNumbers[], disableAlarm(bool),
             alarmDateFrom, alarmDateTo,
             title, summary, content, url, publishFrom, publishTo }
```

### 2.26 Common bus lines GDA+GDY
```
GET .../c2e7b97-0874-473c-aa34-2b3f361f6ec4/download/linie_laczone.json
```
```
[]: { routeID(int) }
```

---

## RELACJE MIĘDZY ZASOBAMI

| Pole | Łączy |
|------|-------|
| `routeId` | routes ↔ departures ↔ trips ↔ stopsInTrips ↔ stopTimes ↔ shapes |
| `stopId` | stops ↔ departures ↔ stopsInTrips ↔ stopTimes |
| `tripId` | trips ↔ stopsInTrips ↔ shapes ↔ expeditionData ↔ stopTimes |
| `agencyId` | agency ↔ routes ↔ trips ↔ stopTimes ↔ dateRanges |
| `vehicleCode` | gpsPositions ↔ vehicleDatabase (nr_inwentarzowy) |
| `displayCode` | displays ↔ displayMessages |
| `zoneId` | stops ↔ crests/herby |

---

## WSKAZÓWKI DEWELOPERSKIE

### Łączenie rozkładu z odjazdami RT
Klucze: `routeId` + `stopId` + `tripId` + `theoreticalTime` (departures) == `departureTime` (stopTimes)

### Łączenie GTFS z GPS
- `trip_id` w GTFS: `<trip>_<Route>_<VehicleService>`
- Route w GPSPositions == część 2 trip_id (po "_")
- VehicleService w GPSPositions == część 3 trip_id

### Łączenie GPS z GTFS (kurs)
Porównaj: Route==trips.Route, VehicleService==trips.VehicleService,
(DataGenerated - Delay) ≈ departure_time ze stop_times.txt

### Linie nocne
Rozkład na dany dzień: zaczyna się wieczorem, kończy następnego dnia (arrivalTime prefix 1899-12-31).

### virtual/nonpassenger/ticketZoneBorder/onDemand
Dla słupka w trasie: OR wartości z stops + stopsInTrips (jeśli "1" w stops → obowiązuje dla wszystkich wystąpień w trasach).
