FROM node:24-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

FROM node:24-alpine AS builder

ARG FORCE_GYM_API_BASE=http://localhost:8080/api

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV FORCE_GYM_API_BASE=${FORCE_GYM_API_BASE}

RUN npm run build

FROM node:24-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000

CMD ["node", "server.js"]