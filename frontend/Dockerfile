# The SPA and the edge are one image: Caddy serves the built bundle and proxies the API and the
# identity provider, so the browser talks to a single origin and there is no CORS to configure.

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# Vite inlines these at build time - they are not runtime configuration, so the image is specific
# to the environment it was built for. Rebuild to point at a different host.
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_OIDC_CLIENT_ID=visionone-web
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_REDIRECT_URI
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID \
    VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY \
    VITE_OIDC_REDIRECT_URI=$VITE_OIDC_REDIRECT_URI

# Fail the build rather than ship a bundle that silently points at localhost.
RUN test -n "$VITE_OIDC_AUTHORITY" || (echo "VITE_OIDC_AUTHORITY build arg is required" && exit 1)
RUN test -n "$VITE_OIDC_REDIRECT_URI" || (echo "VITE_OIDC_REDIRECT_URI build arg is required" && exit 1)
RUN npm run build

FROM caddy:2-alpine AS runtime
COPY --from=build /src/dist /srv
