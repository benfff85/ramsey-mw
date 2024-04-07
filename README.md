# Ramsey Middleware
Middleware component for the Ramsey Project

## Environments
| Environment | Database Address   | Database   | Description                                                              |
|-------------|:-------------------|------------|--------------------------------------------------------------------------|
| Dev         | 192.168.1.224:3306 | ramsey-dev | Database fully reinitialized every startup. Seeded with one sample graph | 

## Image Build and Deploy

Build the image using SpringBoot defaults
```bash
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=benferenchak/ramsey-mw:dev
````

Publish the image to Dockerhub
```bash
docker push benferenchak/ramsey-mw:dev
```

Start a container using the image
```bash
docker run --restart=always \
  --name=ramsey-mw \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_USER=******** \
  -e DB_PASS=******** \
  --cpus=8 \
  -p 8080:8080 \
  benferenchak/ramsey-mw:dev
```

## Swagger
This project is configured with OpenAPI 3.0 documentation, the swagger page can be located at:

[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## TODO
* Add index to work_unit table (`vertex_count`,`subgraph_size`,`status`,`assigned_client`)