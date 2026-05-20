FROM node:20-alpine
WORKDIR /usr/src/app
COPY /source/package*.json ./
RUN npm install && \
    npm install -g serve@14.2.4
COPY /source .
EXPOSE 3002
CMD [ "npm", "start" ]