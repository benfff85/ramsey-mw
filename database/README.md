# Ramsey Database Setup

## Create the MySQL Instance

Create the MySQL instance  with docker by deploying the stack defined in `ramsey-db-stack.yml`

> docker stack deploy ramsey-db --compose-file ramsey-db-stack.yml

## Setup the MySQL Connection

Login with DBeaver, you may need to set the following in the Driver properties tab of the connection details.

1. `allowPublicKeyRetrieval`: True
2. `useSSL`: False

## Initialize DDL

Execute the sqlscript `init-script.ddl` in DBeaver to create the database, users, and tables.



