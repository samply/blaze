#!/bin/bash
set -euo pipefail

{ yes '{"resourceType": "Bundle", "type": "transaction", "entry": [{"fullUrl": "Patient/foo", "request": {"method": "POST", "url": "Patient"}, "resource": {"resourceType": "Patient"}}]}' || true; } | head -n 50000
