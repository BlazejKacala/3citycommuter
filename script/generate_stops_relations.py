import json
import requests
from dataclasses import dataclass
from typing import List
from dataclasses import asdict
import os


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
class BusStop:
    stopId: int
    isForBuses: bool
    isForTrams: bool


# Pobranie JSON z URL-a
url = "https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/3115d29d-b763-4af5-93f6-763b835967d6/download/stopsintrip.json"
response = requests.get(url)
json_data = response.json()

# Deserializacja JSON do obiektu klasy StopData
source = StopData(
    lastUpdate=json_data["2024-02-12"]["lastUpdate"],
    stopsInTrip=[Stop(**stop) for stop in json_data["2024-02-12"]["stopsInTrip"]]
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
for stop_id, info in bus_stops_dict.items():
    is_for_buses = info['isForBuses']
    is_for_trams = info['isForTrams']
    output.append(BusStop(stopId=stop_id, isForBuses=is_for_buses, isForTrams=is_for_trams))
    
output_dict = [asdict(obj) for obj in output]
folder_path = os.path.dirname(os.path.abspath(__file__))

output_file_path = os.path.join(folder_path, 'relations.json')
with open(output_file_path, 'w') as f:
    json.dump(output_dict, f, indent=4)