FROM golang:1.22-alpine AS builder
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN go test ./... && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/crypta-security-verifier .

FROM alpine:3.20
RUN addgroup -S verifier && adduser -S verifier -G verifier
USER verifier
WORKDIR /app
COPY --from=builder /out/crypta-security-verifier /app/crypta-security-verifier
EXPOSE 9090
ENTRYPOINT ["/app/crypta-security-verifier"]
