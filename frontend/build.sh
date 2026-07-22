#!/bin/bash
set -e

sed -i "s|__API_URL__|${API_URL}|g" src/environments/environment.prod.ts
npx ng build --configuration production
