EXEC sp_rename 'goods.pickup_address', 'pickup_city', 'COLUMN';
EXEC sp_rename 'goods.delivery_address', 'delivery_city', 'COLUMN';
EXEC sp_rename 'route_stops.address', 'city', 'COLUMN';

EXEC sp_rename 'geocoded_addresses', 'geocoded_cities';
EXEC sp_rename 'geocoded_cities.address_hash', 'city_hash', 'COLUMN';
EXEC sp_rename 'geocoded_cities.normalized_address', 'normalized_city', 'COLUMN';
