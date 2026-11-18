FROM node:22-alpine
WORKDIR /app
# Starts as root only to hand /data to PUID:PGID, then server.js drops to that user (Unraid: 99:100).
ENV NODE_ENV=production PORT=8080 DATA_DIR=/data PUID=1000 PGID=1000
COPY package.json server.js ./
COPY public ./public
COPY seed ./seed
RUN mkdir -p /data
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
CMD ["node", "server.js"]
