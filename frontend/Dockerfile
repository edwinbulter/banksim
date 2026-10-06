# Build-stage: Angular productie-build
FROM node:24.21.0-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Runtime: nginx als non-root (uid 101), alleen statische bestanden
FROM nginxinc/nginx-unprivileged:1.30.5-alpine@sha256:15c994d10d6d78658721c3bcafff14cb281fba2a4bdf9d5ba92c416a472516e3
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist/bank-web/browser /usr/share/nginx/html
EXPOSE 8443 8081
