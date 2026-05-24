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
