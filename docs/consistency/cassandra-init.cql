ALTER KEYSPACE system_auth WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor' : 3};

CREATE KEYSPACE blaze WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor' : 3};
CREATE TABLE blaze.resources (hash text PRIMARY KEY, content blob);
