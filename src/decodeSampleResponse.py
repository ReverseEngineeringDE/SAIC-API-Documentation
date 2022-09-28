# https://github.com/eerimoq/asn1tools
import asn1tools
# read file
from pathlib import Path

# init asn1tools with ASN.1 schema and PER decoding
asn1Tool = asn1tools.compile_files('ApplicationDataMP.asn1', 'per')

# read RAW-Body post response from response.per
raw_response = Path('response.per').read_text()

# encode to hex
raw_response_hex = raw_response.encode("iso-8859-1").hex()

# encode to byte array for asn1tools lib
byteArray = bytearray.fromhex(raw_response_hex)

# decode VehicleStatusResp with asn1tools
VehicleStatusResp = asn1Tool.decode('VehicleStatusResp', byteArray)

# values do not make sense at all? vehicleData can not be converted? 
# {'vehicleStatus': {'gpsPosition': {'wayPoint': {'position': {'latitude': -89999952, 'longitude': -179999948, 'altitude': 17870}, 'heading': 12599, 'speed': 12616}, 'timestamp': {'seconds': 51}, 'gpsStatus': 'noGpsSignal'}, 'vehicleData': b'53736323930363636313638383433F0F983060C183060C18306'}}
print("\nVehicleStatusResp:")
print (VehicleStatusResp)


MPAlarmResp = asn1Tool.decode('MP-AlarmResp', byteArray)
# values do not make sense at all? 
# {'alarmInfo': {'alarmType': 'moving', 'alarmTime': {'seconds': 3158324}, 'vehicleStatus': {'gpsPosition': {'wayPoint': {'position': {'latitude': -89987151, 'longitude': -179999947, 'altitude': 12236}, 'heading': 13105, 'speed': 12109}, 'timestamp': {'seconds': 55}, 'gpsStatus': 'noGpsSignal'}, 'vehicleData': b'323930363636313638383433F0F983060C183060C183060C183060'}}}
print("\nMP-AlarmResp:")
print (MPAlarmResp)


# see ApplicationDataMP.asn1 to decode more types