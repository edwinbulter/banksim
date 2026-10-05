# Build-stage: Angular productie-build
FROM node:24.21.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Runtime: nginx als non-root (uid 101), alleen statische bestanden
FROM nginxinc/nginx-unprivileged:1.30.5-alpine
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist/bank-web/browser /usr/share/nginx/html
EXPOSE 8443 8081
