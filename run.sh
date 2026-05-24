#!/bin/bash

# ANSI Color codes for beautiful console logging
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0;m' # No Color

echo -e "${BLUE}======================================================${NC}"
echo -e "${BLUE}  Sovereign Communication Platform - Local Deployer   ${NC}"
echo -e "${BLUE}======================================================${NC}"

# Ensure docker daemon is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker daemon is not running. Please start Docker and try again.${NC}"
    exit 1
fi

echo -e "\n${YELLOW}[1/4] Building Docker containers...${NC}"
docker compose build

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Docker build failed!${NC}"
    exit 1
fi

echo -e "\n${YELLOW}[2/4] Launching orchestrator in background...${NC}"
docker compose down
docker compose up -d

echo -e "\n${YELLOW}[3/4] Waiting for services to become healthy...${NC}"
echo -e "Polling database and backend startup status..."

# Health check polling configuration
HEALTH_URL="http://localhost:8080/actuator/health"
MAX_ATTEMPTS=40
ATTEMPT=1
HEALTHY=false

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo -e "Attempt ${ATTEMPT}/${MAX_ATTEMPTS} to contact health check..."
    
    # Send request and extract HTTP response code and body
    RESPONSE=$(curl -s -w "\n%{http_code}" "$HEALTH_URL" 2>/dev/null)
    HTTP_STATUS=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_STATUS" -eq 200 ] && [[ "$BODY" == *"UP"* ]]; then
        echo -e "\n${GREEN}Success! Backend is healthy and responding with status: UP${NC}"
        HEALTHY=true
        break
    fi
    
    sleep 3
    ((ATTEMPT++))
done

if [ "$HEALTHY" = false ]; then
    echo -e "\n${RED}Error: App failed to become healthy within the timeout period.${NC}"
    echo -e "${YELLOW}Dumping application container logs for troubleshooting:${NC}"
    docker compose logs app
    echo -e "${BLUE}======================================================${NC}"
    exit 1
fi

echo -e "\n${YELLOW}[4/4] Verifying API availability...${NC}"
echo -e "Testing mock WebAuthn options endpoint..."
WEBAUTHN_TEST=$(curl -s "http://localhost:8080/api/v1/webauthn/options/41adab42-2b63-4903-8d6b-df2c5d4ef5d1" 2>/dev/null)

if [[ "$WEBAUTHN_TEST" == *"challengeBase64"* ]]; then
    echo -e "${GREEN}API verification successful! Mock endpoint returned standard scaffold-challenge.${NC}"
else
    echo -e "${RED}Warning: WebAuthn verification endpoint returned unexpected payload: ${WEBAUTHN_TEST}${NC}"
fi

echo -e "\n${GREEN}======================================================${NC}"
echo -e "${GREEN}  DEPLOYMENT SUCCESSFUL                                ${NC}"
echo -e "${GREEN}  Local Server running at: http://localhost:8080      ${NC}"
echo -e "${GREEN}  Database exposed at: jdbc:postgresql://localhost:5433/sovereign_comm${NC}"
echo -e "${GREEN}======================================================${NC}"
echo -e "To view logs, run:       docker compose logs -f"
echo -e "To shut down, run:       docker compose down"
echo -e "${GREEN}======================================================${NC}"
