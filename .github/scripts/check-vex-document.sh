#!/bin/bash
set -euo pipefail

# Validates the OpenVEX document given as first argument against the JSON
# schema of the OpenVEX spec at commit OPENVEX_SPEC_COMMIT and checks the
# identifiers the schema leaves unchecked.

document="$1"
schema="$(mktemp)"

curl -sSfL "https://raw.githubusercontent.com/openvex/spec/$OPENVEX_SPEC_COMMIT/openvex_json_schema.json" > "$schema"
echo "$OPENVEX_SCHEMA_CHECKSUM $schema" | sha256sum -c

pipx run --spec "check-jsonschema==$CHECK_JSONSCHEMA_VERSION" check-jsonschema --schemafile "$schema" "$document"

# The schema only requires the IDs to be strings. Checks that:
# * every product is a Blaze image on one of the registries Blaze is pushed to,
# * every product has subcomponents, which are PURLs with a version and
#   without qualifiers,
# * every vulnerability name and alias is a CVE or GHSA ID,
# * every statement lists both registries of an image with the same
#   subcomponents.
problems="$(jq -r '
  def products:
    ["blaze", "blaze-frontend"][] as $name
    | ["index.docker.io", "ghcr.io"][] as $registry
    | "pkg:oci/\($name)?repository_url=\($registry)%2Fsamply%2F\($name)";
  def vulnerability_id: test("^(CVE-[0-9]{4}-[0-9]{4,}|GHSA(-[23456789cfghjmpqrvwx]{4}){3})$");
  def versioned_purl: test("^pkg:[a-z]+/[^?#@]+@[^?#@]+$");
  def image: ."@id" | sub("\\?.*$"; "");

  .statements | to_entries[] | "statement \(.key)" as $statement | .value
  | (.vulnerability.name | select(vulnerability_id | not) | "\($statement): invalid vulnerability name \(.)"),
    (.vulnerability.aliases // [] | .[] | select(vulnerability_id | not) | "\($statement): invalid vulnerability alias \(.)"),
    (.products[] | ."@id" | select(IN(products) | not) | "\($statement): unknown product \(.)"),
    (.products[] | select((.subcomponents // []) | length == 0) | "\($statement): product \(."@id") has no subcomponents"),
    (.products[] | .subcomponents // [] | .[] | ."@id" | select(versioned_purl | not)
      | "\($statement): subcomponent \(.) is no PURL with a version and without qualifiers"),
    (.products | group_by(image)[]
      | select(length != 2 or (map([.subcomponents // [] | .[]."@id"] | sort) | unique | length) != 1)
      | "\($statement): \(.[0] | image) needs both registries with the same subcomponents")
' "$document")"

if [ -n "$problems" ]; then
  echo "🆘 the VEX document has problems:"
  echo "$problems"
  exit 1
fi

echo "✅ the IDs of the VEX document are valid"
