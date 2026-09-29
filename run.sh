#!/bin/bash

down="$1"

if [ "$down" != "" ]; then
  docker compose -f docker-compose.yml down
else
  docker compose -f docker-compose.yml up
fi