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
