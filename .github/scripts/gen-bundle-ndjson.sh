#!/bin/bash -e

yes '{"resourceType": "Bundle", "type": "transaction", "entry": [{"fullUrl": "Patient/foo", "request": {"method": "POST", "url": "Patient"}, "resource": {"resourceType": "Patient"}}]}' | head -n 50000
