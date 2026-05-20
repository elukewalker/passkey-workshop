FROM node:20-alpine
WORKDIR /usr/src/app
COPY /source/package*.json ./
RUN npm install
COPY /source .
EXPOSE 3002
CMD [ "npm", "start" ]