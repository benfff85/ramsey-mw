# Ramsey Middleware
Middleware component for the Ramsey Project

## Environments
| Environment | Database Address     | Database   | Description                                                                                  |
|-------------|:---------------------|------------|----------------------------------------------------------------------------------------------|
| Local       | 127.0.0.1:36001      | ramsey-dev | Database not initialized, designed to be used for running in the IDE.                        |
| Dev         | ramsey-db-mysql:3306 | ramsey-dev | Database not initialized, designed for running in Docker and connecting to the dev database. | 

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
  --network=ramsey-net \
  --label com.docker.compose.project=ramsey \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_USER=ramsey-user-dev \
  -e DB_PASS=<password> \
  -p 36000:8080 \
  benferenchak/ramsey-mw:develop
```

Likewise this can be deployed as part of the docker-compose.yml file.

```bash
docker compose -f ./docker/ramsey-compose.yml -p ramsey up --scale ramsey-mw=1 --scale ramsey-queue-manager=1 --scale ramsey-worker-rust=10 --scale ramsey-ui=1 -d
```

## Port Mappings

| Service | External Port | Internal Port |
|---------|---------------|---------------|
| MW | 36000 | 8080 |
| Database | 36001 | 3306 |
| Redis | 36002 | 6379 |
| UI | 36003 | 8501 |

## Swagger

This project is configured with OpenAPI 3.0 documentation, the swagger page can be located at:

[http://localhost:36000/swagger-ui/index.html](http://localhost:36000/swagger-ui/index.html)

## Common Queries

Find the count of results by stage:
```sql
SELECT stage_id, COUNT(1) 
FROM `ramsey-dev`.work_result 
GROUP BY stage_id;
```

## Misc

Sample Numbers

| Item                | Count       |
|---------------------|-------------|
| Vertex              | 288         |
| Edges               | 41,328      |
| Edges of each color | 20,664      |
| Single Mutations    | 427,000,896 |