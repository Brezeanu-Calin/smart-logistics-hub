UPDATE g
SET status = 'ASSIGNED'
FROM goods g
WHERE g.status = 'UNASSIGNED'
  AND g.archived = 0
  AND EXISTS (
      SELECT 1
      FROM route_stops stop
      JOIN routes route ON route.id = stop.route_id
      WHERE stop.goods_id = g.id
        AND route.status = 'PLANNED'
        AND route.archived = 0
  );
