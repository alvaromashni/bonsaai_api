#!/bin/bash

# Script para iniciar o ambiente de desenvolvimento completo
# Autor: Claude Code
# Data: 2026-01-11

set -e

echo "🚀 Iniciando ambiente de desenvolvimento da Habits API..."
echo ""

# Cores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Verificar se Docker está rodando
echo "📦 Verificando Docker..."
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker não está rodando!${NC}"
    echo "Por favor, inicie o Docker Desktop e tente novamente."
    exit 1
fi
echo -e "${GREEN}✅ Docker está rodando${NC}"
echo ""

# Iniciar PostgreSQL e Redis
echo "🐘 Iniciando PostgreSQL e Redis..."
docker-compose up -d

# Aguardar serviços ficarem prontos
echo "⏳ Aguardando serviços ficarem prontos..."
sleep 5

# Verificar se os containers estão rodando
if docker ps | grep -q "habits-postgres"; then
    echo -e "${GREEN}✅ PostgreSQL está rodando${NC}"
else
    echo -e "${RED}❌ PostgreSQL falhou ao iniciar${NC}"
    exit 1
fi

if docker ps | grep -q "habits-redis"; then
    echo -e "${GREEN}✅ Redis está rodando${NC}"
else
    echo -e "${RED}❌ Redis falhou ao iniciar${NC}"
    exit 1
fi

echo ""
echo "🔧 Verificando arquivo .env..."
if [ ! -f .env ]; then
    echo -e "${RED}❌ Arquivo .env não encontrado!${NC}"
    echo "Copiando .env.example para .env..."
    cp .env.example .env
    echo -e "${YELLOW}⚠️  Por favor, configure suas variáveis no arquivo .env${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Arquivo .env existe${NC}"
echo ""

# Iniciar o backend
echo "☕ Iniciando backend Spring Boot..."
echo "Isso pode levar alguns minutos na primeira vez..."
echo ""

./mvnw spring-boot:run
