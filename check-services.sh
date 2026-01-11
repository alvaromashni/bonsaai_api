#!/bin/bash

# Script para verificar status dos serviços
# Autor: Claude Code
# Data: 2026-01-11

echo "🔍 Verificando status dos serviços..."
echo ""

# Cores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Verificar PostgreSQL
if docker ps | grep -q "habits-postgres"; then
    echo -e "${GREEN}✅ PostgreSQL está rodando (porta 5432)${NC}"
else
    echo -e "${RED}❌ PostgreSQL NÃO está rodando${NC}"
fi

# Verificar Redis
if docker ps | grep -q "habits-redis"; then
    echo -e "${GREEN}✅ Redis está rodando (porta 6379)${NC}"
else
    echo -e "${RED}❌ Redis NÃO está rodando${NC}"
fi

# Verificar Backend (porta 8080)
if lsof -ti:8080 > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Backend está rodando (porta 8080)${NC}"
else
    echo -e "${RED}❌ Backend NÃO está rodando${NC}"
fi

echo ""
echo "📊 Containers Docker ativos:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "habits-|NAMES"

echo ""
echo "🌐 URLs disponíveis:"
echo "   Backend API: http://localhost:8080"
echo "   PostgreSQL: localhost:5432"
echo "   Redis: localhost:6379"
