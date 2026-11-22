#!/bin/bash
set -euo pipefail

# Generates the keys and documents served by the fake OpenID provider of the
# openid-auth-limits-test and the tokens sent by check.sh.
#
# Usage: generate.sh documents <out-dir>
#        generate.sh tokens <out-dir>
#
# `documents` writes, before `docker compose up`:
#  <out-dir>/keys/<name>.pem    (private keys)
#  <out-dir>/www/<scenario>/.well-known/openid-configuration
#  <out-dir>/www/<scenario>/jwks.json
#  <out-dir>/k1-n               (the modulus of K1, to check logs for key material)
#
# `tokens` writes <out-dir>/tokens/<name>, which are never printed. Run it
# right before check.sh, so that the times relative to now (`exp` 30 s ago,
# `nbf` in 30 s) are still inside the leeway when the requests are sent.

command="$1"
out="$2"
provider="http://provider"
audience="blaze"


b64url() {
  basenc --base64url -w0 | tr -d '='
}

# generates the RSA key $1 with $2 bits and the optional extra args of openssl
gen_key() {
  local name="$1" bits="$2"
  shift 2
  openssl genrsa "$@" -out "$out/keys/$name.pem" "$bits" 2>/dev/null
}

# prints the base64url modulus of key $1
modulus() {
  openssl rsa -in "$out/keys/$1.pem" -noout -modulus 2>/dev/null | cut -d= -f2 | basenc --base16 -d | b64url
}

empty_object='{}'
default_header='{alg: "RS256", typ: "JWT"}'

# prints the public JWK of key $1 with kid $1, the base64url exponent $2 and
# optional extra members $3 (a jq object expression)
jwk() {
  local extra="${3:-$empty_object}" n
  n=$(modulus "$1")
  jq -cn --arg kid "$1" --arg n "$n" --arg e "$2" \
    "{kty: \"RSA\", use: \"sig\", alg: \"RS256\", kid: \$kid, n: \$n, e: \$e} + $extra"
}

# signs the raw header $2 and raw payload $3 with key $1 (RS256)
sign_raw() {
  local input
  input="$(printf '%s' "$2" | b64url).$(printf '%s' "$3" | b64url)"
  printf '%s.%s' "$input" "$(printf '%s' "$input" | openssl dgst -sha256 -sign "$out/keys/$1.pem" -binary | b64url)"
}

# writes the token $1 signed by key $2 with the raw header $3 and raw payload $4
token_raw() {
  sign_raw "$2" "$3" "$4" > "$out/tokens/$1"
}

# prints the claims for issuer $1, modified by the optional jq filter $2
claims() {
  local filter="${2:-.}"
  jq -cn --arg iss "$1" --arg aud "$audience" --argjson now "$now" \
    "{sub: \"user\", iss: \$iss, aud: \$aud, exp: (\$now + 300), iat: \$now} | $filter"
}

# writes the token $1 signed by key $2 for issuer $3 with the jq header
# expression $4 and the jq claims filter $5
token() {
  local header claims
  header=$(jq -cn "${4:-$default_header}")
  claims=$(claims "$3" "${5:-.}")
  token_raw "$1" "$2" "$header" "$claims"
}

# writes the OpenID configuration of scenario $1
openid_config() {
  mkdir -p "$out/www/$1/.well-known"
  jq -cn --arg iss "$provider/$1" '{issuer: $iss, jwks_uri: ($iss + "/jwks.json")}' \
    > "$out/www/$1/.well-known/openid-configuration"
}

# pads the JSON object document $1 with an unknown member to exactly $2 bytes
pad_document() {
  local file="$1" size="$2" chunk len rest
  chunk=$(printf 'a%.0s' $(seq 1000))
  # remove the closing brace (and a trailing newline) and add an unknown member
  # of 1000 character strings
  printf '%s' "$(cat "$file")" | head -c -1 > "$file.tmp"
  printf ',"pad":["%s"' "$chunk" >> "$file.tmp"
  while true; do
    len=$(stat -c %s "$file.tmp")
    if (( len + 1004 + 2 > size )); then break; fi
    printf ',"%s"' "$chunk" >> "$file.tmp"
  done
  printf ']' >> "$file.tmp"
  len=$(stat -c %s "$file.tmp")
  rest=$(( size - len - 1 ))
  printf '%*s}' "$rest" "" >> "$file.tmp"
  mv "$file.tmp" "$file"
}

# prints a JWKS with K1 as only key, with the extra members $1 (a jq object
# expression)
single_jwks() {
  local jwk
  jwk=$(jwk k1 AQAB "${1:-$empty_object}")
  jq -cn --argjson jwk "$jwk" '{keys: [$jwk]}'
}

# prints $1 objects nested into each other
nested() {
  local depth="$1" s="1" i
  for (( i = 0; i < depth; i++ )); do s="{\"a\":$s}"; done
  printf '%s' "$s"
}

digits() { printf '1%.0s' $(seq "$1"); }
letters() { printf 'a%.0s' $(seq "$1"); }

# adds the raw member $2 to the raw JSON object $1
with_member() { printf '%s,%s}' "${1%\}}" "$2"; }

# flips the character at position 10 of segment $2 (0-based) of token file $1
flip() {
  local token seg i parts c
  token=$(cat "$out/tokens/$1")
  IFS=. read -r -a parts <<< "$token"
  seg="${parts[$2]}"
  c="${seg:10:1}"
  if [ "$c" = "A" ]; then c=B; else c=A; fi
  parts[$2]="${seg:0:10}$c${seg:11}"
  i=$(IFS=.; echo "${parts[*]}")
  printf '%s' "$i"
}

documents() {
  local i
  rm -rf "$out/keys" "$out/www"
  mkdir -p "$out/keys" "$out/www"

  echo "Generate keys..."
  gen_key k1 2048
  gen_key k1024 1024
  gen_key k8200 8200
  gen_key ke3 2048 -3
  gen_key attacker 2048
  for i in $(seq 1 16); do gen_key "g$i" 2048; done
  modulus k1 > "$out/k1-n"

  # scenario mixed: K1 plus keys to be skipped and 16 further good keys,
  # so that 17 keys are usable
  openid_config mixed
  {
    jwk k1 AQAB
    jwk k1024 AQAB
    jwk k8200 AQAB
    jwk ke3 Aw
    for i in $(seq 1 16); do jwk "g$i" AQAB; done
  } | jq -cs '{keys: .}' > "$out/www/mixed/jwks.json"

  # document-level scenarios, all with K1 as only key
  openid_config jwks-too-large
  single_jwks > "$out/www/jwks-too-large/jwks.json"
  pad_document "$out/www/jwks-too-large/jwks.json" $(( 1048576 + 1 ))

  openid_config jwks-too-deep
  single_jwks '{x: [[[[[{a: 1}]]]]]}' > "$out/www/jwks-too-deep/jwks.json"

  openid_config jwks-duplicate-keys
  single_jwks | sed 's/"e":/"n":"AQAB","e":/' > "$out/www/jwks-duplicate-keys/jwks.json"

  openid_config jwks-long-string
  single_jwks "{x5c: [\"$(letters 16385)\"]}" > "$out/www/jwks-long-string/jwks.json"

  openid_config config-too-large
  single_jwks > "$out/www/config-too-large/jwks.json"
  pad_document "$out/www/config-too-large/.well-known/openid-configuration" $(( 1048576 + 1 ))

  chmod -R a+rX "$out/www"
  echo "Done generating the documents."
}

tokens() {
  local iss claims_raw hs_input secret scenario
  rm -rf "$out/tokens"
  mkdir -p "$out/tokens"
  now=$(date +%s)
  iss="$provider/mixed"

  token valid k1 "$iss"
  token typ-at-jwt k1 "$iss" '{alg: "RS256", typ: "at+jwt"}'
  token aud-array k1 "$iss" '' '. + {aud: ["other", "blaze"]}'
  token kid-of-other-key k1 "$iss" '{alg: "RS256", kid: "g1"}'
  token by-k1024 k1024 "$iss"
  token by-k8200 k8200 "$iss"
  token by-e3 ke3 "$iss"
  token by-16th-key g15 "$iss"
  token by-17th-key g16 "$iss"

  token alg-none k1 "$iss" '{alg: "none"}'
  token alg-hs256 k1 "$iss" '{alg: "HS256"}'
  token alg-rs256-lower k1 "$iss" '{alg: "rs256"}'
  token crit k1 "$iss" '{alg: "RS256", crit: ["b64"], b64: false}'
  token jwk-attacker attacker "$iss" "{alg: \"RS256\", jwk: $(jwk attacker AQAB)}"

  # alg HS256 with an HMAC signature using K1's public key bytes as secret
  hs_input="$(printf '{"alg":"HS256"}' | b64url).$(claims "$iss" | tr -d '\n' | b64url)"
  secret=$(openssl rsa -in "$out/keys/k1.pem" -pubout -outform DER 2>/dev/null | basenc --base16 -w0)
  printf '%s.%s' "$hs_input" "$(printf '%s' "$hs_input" | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$secret" -binary | b64url)" \
    > "$out/tokens/hmac-public-key"

  # alg none with an empty signature
  printf '%s.%s.' "$(printf '{"alg":"none"}' | b64url)" "$(claims "$iss" | tr -d '\n' | b64url)" \
    > "$out/tokens/alg-none-unsigned"

  flip valid 2 > "$out/tokens/flipped-signature"
  flip valid 1 > "$out/tokens/flipped-payload"

  token exp-missing k1 "$iss" '' 'del(.exp)'
  token exp-120s-ago k1 "$iss" '' ". + {exp: ($now - 120)}"
  token nbf-in-120s k1 "$iss" '' ". + {nbf: ($now + 120)}"
  token exp-30s-ago k1 "$iss" '' ". + {exp: ($now - 30)}"
  token nbf-in-30s k1 "$iss" '' ". + {nbf: ($now + 30)}"
  token wrong-iss k1 "https://other.example"
  token aud-missing k1 "$iss" '' 'del(.aud)'
  token aud-array-without k1 "$iss" '' '. + {aud: ["other"]}'

  # JSON at the limits and beyond in header and payload
  claims_raw="$(claims "$iss" | tr -d '\n')"
  token_raw header-depth-8 k1 "{\"alg\":\"RS256\",\"x\":$(nested 7)}" "$claims_raw"
  token_raw header-depth-9 k1 "{\"alg\":\"RS256\",\"x\":$(nested 8)}" "$claims_raw"
  token_raw payload-depth-8 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"x\":$(nested 7)")"
  token_raw payload-depth-9 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"x\":$(nested 8)")"
  token_raw number-32 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"x\":$(digits 32)")"
  token_raw number-33 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"x\":$(digits 33)")"
  token_raw name-256 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"$(letters 256)\":1")"
  token_raw name-257 k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"$(letters 257)\":1")"
  token_raw header-duplicate k1 '{"alg":"RS256","alg":"RS256"}' "$claims_raw"
  token_raw payload-duplicate k1 '{"alg":"RS256"}' "$(with_member "$claims_raw" "\"sub\":\"other\"")"

  # accepted by legacy mode only
  token_raw legacy-no-exp k1 '{"alg":"RS256"}' "$(claims "$iss" 'del(.exp)' | tr -d '\n')"
  token_raw legacy-alg-none k1 '{"alg":"none"}' "$claims_raw"
  token_raw legacy-crit k1 '{"alg":"RS256","crit":["b64"],"b64":false}' "$claims_raw"
  token legacy-any-iss-aud k1 "https://any.example" '' '. + {aud: "any"}'

  for scenario in jwks-too-large jwks-too-deep jwks-duplicate-keys jwks-long-string config-too-large; do
    token "doc-$scenario" k1 "$provider/$scenario"
  done

  echo "Done generating $(find "$out/tokens" -type f | wc -l) tokens."
}

case "$command" in
  documents) documents ;;
  tokens) tokens ;;
  *) echo "Usage: $0 documents|tokens <out-dir>"; exit 1 ;;
esac
