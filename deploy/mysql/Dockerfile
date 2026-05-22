FROM mysql:8.0.39

ENV MYSQL_ROOT_PASSWORD ${DATABASE_PASSWORD}
ADD db.sql /docker-entrypoint-initdb.d

EXPOSE 3306
