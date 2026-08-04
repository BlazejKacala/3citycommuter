import os
import json
from dataclasses import asdict
from dataclasses import dataclass
from typing import List

import requests


@dataclass
class Stop:
    routeId: int
    tripId: int
    stopId: int
    stopSequence: int
    agencyId: int
    topologyVersionId: int
    passenger: bool
    tripActivationDate: str
    stopActivationDate: str

@dataclass
class StopData:
    lastUpdate: str
    stopsInTrip: List[Stop]

@dataclass
class TransitStopKey:
    provider: str
    sourceStopId: int


@dataclass
class BusStop:
    stopKey: TransitStopKey
    isForBuses: bool
    isForTrams: bool


# Pobranie JSON z URL-a
url = "https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/3115d29d-b763-4af5-93f6-763b835967d6/download/stopsintrip.json"
response = requests.get(url)
json_data = response.json()

# Deserializacja najnowszego snapshotu danych do obiektu klasy StopData
latest_snapshot_key = max(json_data.keys())
latest_version = json_data[latest_snapshot_key]
source = StopData(
    lastUpdate=latest_version["lastUpdate"],
    stopsInTrip=[Stop(**stop) for stop in latest_version["stopsInTrip"]]
).stopsInTrip

bus_stops_dict = {}
    
for stop in source:
    stop_id = stop.stopId
    route_id = stop.routeId
        
    if stop_id not in bus_stops_dict:
        bus_stops_dict[stop_id] = {
            'isForBuses': False,
            'isForTrams': False
        }
        
    if route_id > 99:
        bus_stops_dict[stop_id]['isForBuses'] = True
    else:
        bus_stops_dict[stop_id]['isForTrams'] = True
    
output = []
for stop_id, info in sorted(bus_stops_dict.items()):
    is_for_buses = info['isForBuses']
    is_for_trams = info['isForTrams']
    output.append(
        BusStop(
            stopKey=TransitStopKey(provider="GDANSK", sourceStopId=stop_id),
            isForBuses=is_for_buses,
            isForTrams=is_for_trams,
        )
    )
    
output_dict = [asdict(obj) for obj in output]
root_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
output_file_path = os.path.join(root_path, "composeApp", "android", "src", "main", "assets", "relations.json")
with open(output_file_path, 'w', encoding='utf-8') as f:
    json.dump(output_dict, f, indent=4)
