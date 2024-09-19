# Ramsey Database Setup

## Inject Password to Configs

```bash
export mysql_pass="<password>" 
sed "s/<password>/$mysql_pass/g" ramsey-db-compose.yml > ramsey-db-compose-sensitive.yml
sed "s/<password>/$mysql_pass/g" init-script.ddl > init-script-sensitive.ddl
```

## Create the MySQL Instance

Create the MySQL instance  with docker by deploying the compose defined in `ramsey-db-compose.yml`

```bash
docker compose -f ramsey-db-compose-sensitive.yml -p ramsey-db up -d
```

## Initialize DDL

Create the databases, users, and tables.

```bash
mysql -u root -p -h 127.0.0.1 -P 3306 < init-script-sensitive.ddl
```

## Initialize Data

For development this will insert a sample record into the `graph` table.

```bash
mysql -u root -p -h 127.0.0.1 -P 3306 < init-data.sql
```

## Set up the MySQL Connection

Login with DBeaver, you may need to set the following in the Driver properties tab of the connection details.

1. `allowPublicKeyRetrieval`: True
2. `useSSL`: False
