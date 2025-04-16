# Ramsey Middleware
Middleware component for the Ramsey Project

## Environments
| Environment | Database Address     | Database   | Description                                                                                  |
|-------------|:---------------------|------------|----------------------------------------------------------------------------------------------|
| Local       | 127.0.0.1:3306       | ramsey-dev | Database not initialized, designed to be used for running in the IDE.                        |
| Dev         | ramsey-db-mysql:3306 | ramsey-dev | Database not initialized ,designed for running in Docker and connecting to the dev database. | 

## Image Build and Deploy

Build the image using SpringBoot defaults
```bash
docker build -t benferenchak/ramsey-mw:develop .
```

Publish the image to Dockerhub
```bash
docker push benferenchak/ramsey-mw:develop
```

Start a container using the image by either directly creating one as follows:
```bash
docker run --restart=always \
  --name=ramsey-mw \
  --network=ramsey-db_ramsey-net \
  --label com.docker.compose.project=ramsey \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_USER=ramsey-user-dev \
  -e DB_PASS=<password> \
  -p 9080:8080 \
  benferenchak/ramsey-mw:develop
```

Likewise this can be deployed as part of the docker-compose.yml file.

```bash
docker compose -f ./docker/ramsey-compose.yml -p ramsey up --scale ramsey-worker=2 -d
```

## Swagger

This project is configured with OpenAPI 3.0 documentation, the swagger page can be located at:

[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## GraphQL

GraphQL can be explored using the GraphiQL page at:

[http://localhost:36000/graphiql](http://localhost:36000/graphiql)

To fetch a stage summary use the following query:

```graphql
query {
    summary {
        stageSummary(stageId: 1, workUnitStatusList: [NEW]) {
            stageId
            workUnitCount
            workUnitStatusList
        }
    }
}
```

## Common Queries

Find the average work unit analysis duration by analysis type 
```sql
SELECT work_unit_analysis_type, AVG(TIMESTAMPDIFF(MICROSECOND , processing_started_date, completed_date)) / 1000000 AS seconds
FROM work_unit
WHERE status = 'COMPLETE'
GROUP BY work_unit_analysis_type;
```

Find any work unit where different analysis types produced differing clique counts
```sql
SELECT edges_to_flip
FROM (SELECT edges_to_flip, COUNT(1)
      FROM work_unit
      WHERE status = 'COMPLETE'
      GROUP BY edges_to_flip, clique_count) tmp
GROUP BY edges_to_flip
HAVING COUNT(1) > 1
```

## Misc

Sample Numbers

| Item                | Count       |
|---------------------|-------------|
| Vertex              | 288         |
| Edges               | 41,328      |
| Edges of each color | 20,664      |
| Single Mutations    | 427,000,896 |