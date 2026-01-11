#!/bin/bash

# Script para parar o ambiente de desenvolvimento
# Autor: Claude Code
# Data: 2026-01-11

set -e

echo "🛑 Parando ambiente de desenvolvimento da Habits API..."
echo ""

# Cores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Parar Docker Compose
echo "📦 Parando PostgreSQL e Redis..."
docker-compose down

echo ""
echo -e "${GREEN}✅ Ambiente de desenvolvimento parado com sucesso!${NC}"
echo ""
echo "Para reiniciar, execute: ./start-dev.sh"
