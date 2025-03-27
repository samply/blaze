#!/bin/bash

## Creates the data needed for the Clojure var: blaze.terminology-service.local.code-system.sct.context/published-release-versions

curl -s https://mlds.ihtsdotools.org/api/releasePackages | jq -rc '.[] | select(.releasePackageURI != null) | select(.releasePackageURI | startswith("http://snomed.info/sct")) | [(.releasePackageURI | split("/") | last | tonumber), ([(.releaseVersions[].versionURI | strings | split("/") | last | tonumber)] | sort)]'
