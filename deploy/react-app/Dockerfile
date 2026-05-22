FROM node:20-alpine
WORKDIR /usr/src/app
COPY /source/package*.json ./
RUN npm install && \
    npm install -g serve@14.2.4
COPY /source .
RUN npm run build
EXPOSE 3000
ENTRYPOINT [ "serve", "-s", "build" ]
