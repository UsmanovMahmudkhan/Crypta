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
