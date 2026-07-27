import argparse
import csv
import gzip
import json
import zipfile
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from io import BytesIO, StringIO
from pathlib import Path
from typing import Dict, List, Optional
from urllib.request import urlopen


TRIPS_URL = "http://api.zdiz.gdynia.pl/pt/trips"
GTFS_URL = "http://api.zdiz.gdynia.pl/pt/gtfs.zip"

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE_DIR = REPO_ROOT / "script" / "gdynia_source"
DEFAULT_TRIPS_PATH = DEFAULT_SOURCE_DIR / "trips.json"
DEFAULT_GTFS_PATH = DEFAULT_SOURCE_DIR / "gtfs.zip"
DEFAULT_DEPARTURE_OUTPUT_PATH = (
    REPO_ROOT / "composeApp" / "android" / "src" / "main" / "assets" / "gdynia" / "departure_match_index.json.gz"
)
DEFAULT_SHAPE_OUTPUT_PATH = (
    REPO_ROOT / "composeApp" / "android" / "src" / "main" / "assets" / "gdynia" / "shape_index.json.gz"
)


@dataclass
class StopTimeMatch:
    time: str
    tripId: int
    headsign: Optional[str]


@dataclass
class StopTimeIndexEntry:
    stopId: int
    departures: List[StopTimeMatch]


@dataclass
class DepartureMatchIndex:
    generatedAtUtc: str
    sourceGtfs: str
    stopTimeIndex: List[StopTimeIndexEntry]


@dataclass
class RoutePoint:
    latitude: float
    longitude: float


@dataclass
class ShapeRoute:
    shapeId: int
    points: List[RoutePoint]


@dataclass
class TripShape:
    tripId: int
    shapeId: int


@dataclass
class ShapeIndex:
    generatedAtUtc: str
    sourceTrips: str
    sourceGtfs: str
    tripShapes: List[TripShape]
    shapeRoutes: List[ShapeRoute]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate separate offline Gdynia indices for departure matching and route geometry.",
    )
    parser.add_argument("--trips", type=Path, default=DEFAULT_TRIPS_PATH, help="Path to trips.json")
    parser.add_argument("--gtfs", type=Path, default=DEFAULT_GTFS_PATH, help="Path to gtfs.zip")
    parser.add_argument(
        "--departure-output",
        type=Path,
        default=DEFAULT_DEPARTURE_OUTPUT_PATH,
        help="Path to the generated departure_match_index.json.gz",
    )
    parser.add_argument(
        "--shape-output",
        type=Path,
        default=DEFAULT_SHAPE_OUTPUT_PATH,
        help="Path to the generated shape_index.json.gz",
    )
    parser.add_argument(
        "--download",
        action="store_true",
        help="Download fresh trips.json and gtfs.zip from the official Gdynia API before generating the indices.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.trips.parent.mkdir(parents=True, exist_ok=True)
    args.gtfs.parent.mkdir(parents=True, exist_ok=True)
    args.departure_output.parent.mkdir(parents=True, exist_ok=True)
    args.shape_output.parent.mkdir(parents=True, exist_ok=True)

    if args.download:
        trips_text = download_text(TRIPS_URL)
        gtfs_bytes = download_bytes(GTFS_URL)
        args.trips.write_text(trips_text, encoding="utf-8")
        args.gtfs.write_bytes(gtfs_bytes)
        source_trips = TRIPS_URL
        source_gtfs = GTFS_URL
    else:
        trips_text = args.trips.read_text(encoding="utf-8")
        gtfs_bytes = args.gtfs.read_bytes()
        source_trips = str(args.trips)
        source_gtfs = str(args.gtfs)

    generated_at = datetime.now(timezone.utc).isoformat()
    departure_index = DepartureMatchIndex(
        generatedAtUtc=generated_at,
        sourceGtfs=source_gtfs,
        stopTimeIndex=parse_stop_time_index(gtfs_bytes),
    )
    shape_index = ShapeIndex(
        generatedAtUtc=generated_at,
        sourceTrips=source_trips,
        sourceGtfs=source_gtfs,
        tripShapes=parse_trip_shapes(trips_text),
        shapeRoutes=parse_shape_routes(gtfs_bytes),
    )

    write_gzip_json(
        args.departure_output,
        asdict(departure_index),
    )
    write_gzip_json(
        args.shape_output,
        asdict(shape_index),
    )

    print(
        f"Generated {args.departure_output} ({len(departure_index.stopTimeIndex)} stops, "
        f"{sum(len(entry.departures) for entry in departure_index.stopTimeIndex)} matches)"
    )
    print(
        f"Generated {args.shape_output} ({len(shape_index.tripShapes)} trip mappings, "
        f"{len(shape_index.shapeRoutes)} shapes, "
        f"{sum(len(route.points) for route in shape_index.shapeRoutes)} points)"
    )


def download_text(url: str) -> str:
    with urlopen(url) as response:
        return response.read().decode("utf-8")


def download_bytes(url: str) -> bytes:
    with urlopen(url) as response:
        return response.read()


def write_gzip_json(path: Path, payload: dict) -> None:
    with gzip.open(path, "wt", encoding="utf-8") as output:
        json.dump(payload, output, ensure_ascii=False, separators=(",", ":"))


def parse_trip_shapes(content: str) -> List[TripShape]:
    trips = json.loads(content)
    return [
        TripShape(tripId=int(trip["tripId"]), shapeId=int(trip["shapeId"]))
        for trip in trips
        if trip.get("tripId") is not None and trip.get("shapeId") is not None
    ]


def parse_shape_routes(gtfs_bytes: bytes) -> List[ShapeRoute]:
    shapes_csv = read_zip_entry(gtfs_bytes, "shapes.txt")
    rows = csv.DictReader(StringIO(shapes_csv))
    points_by_shape_id: Dict[int, List[tuple[int, RoutePoint]]] = defaultdict(list)

    for row in rows:
        shape_id = parse_int(row.get("shape_id"))
        latitude = parse_float(row.get("shape_pt_lat"))
        longitude = parse_float(row.get("shape_pt_lon"))
        sequence = parse_int(row.get("shape_pt_sequence"))
        if shape_id is None or latitude is None or longitude is None or sequence is None:
            continue
        points_by_shape_id[shape_id].append((sequence, RoutePoint(latitude=latitude, longitude=longitude)))

    return [
        ShapeRoute(
            shapeId=shape_id,
            points=[point for _, point in sorted(points_by_shape_id[shape_id], key=lambda item: item[0])],
        )
        for shape_id in sorted(points_by_shape_id)
    ]


def parse_stop_time_index(gtfs_bytes: bytes) -> List[StopTimeIndexEntry]:
    stop_times_csv = read_zip_entry(gtfs_bytes, "stop_times.txt")
    rows = csv.DictReader(StringIO(stop_times_csv))
    departures_by_stop_id: Dict[int, List[StopTimeMatch]] = defaultdict(list)

    for row in rows:
        stop_id = parse_int(first_present(row, "stopId", "stop_id"))
        trip_id = parse_int(first_present(row, "tripId", "trip_id"))
        departure_time = first_present(row, "departureTime", "departure_time")
        if stop_id is None or trip_id is None or not departure_time:
            continue

        departures_by_stop_id[stop_id].append(
            StopTimeMatch(
                time=short_time(departure_time),
                tripId=trip_id,
                headsign=first_present(row, "stopHeadsign", "stop_headsign"),
            ),
        )

    return [
        StopTimeIndexEntry(
            stopId=stop_id,
            departures=sorted(
                departures_by_stop_id[stop_id],
                key=lambda departure: (departure.time, departure.headsign or "", departure.tripId),
            ),
        )
        for stop_id in sorted(departures_by_stop_id)
    ]


def read_zip_entry(gtfs_bytes: bytes, entry_name: str) -> str:
    with zipfile.ZipFile(BytesIO(gtfs_bytes)) as archive:
        with archive.open(entry_name) as entry:
            return entry.read().decode("utf-8-sig")


def short_time(value: str) -> str:
    return ":".join(value.split(":")[:2])


def first_present(row: Dict[str, str], *keys: str) -> Optional[str]:
    for key in keys:
        value = row.get(key)
        if value not in (None, ""):
            return value
    return None


def parse_int(value: Optional[str]) -> Optional[int]:
    if value in (None, ""):
        return None
    try:
        return int(value)
    except ValueError:
        return None


def parse_float(value: Optional[str]) -> Optional[float]:
    if value in (None, ""):
        return None
    try:
        return float(value)
    except ValueError:
        return None


if __name__ == "__main__":
    main()
