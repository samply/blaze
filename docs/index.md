---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Blaze"
  text: "FHIR® Server with internal, fast CQL Engine"
  actions:
  - theme: alt
    text: FHIR API
    link: /api
  - theme: brand
    text: Get Started
    link: /deployment
  - theme: alt
    text: View on GitHub
    link: https://github.com/samply/blaze

features:
- title: Internal, fast CQL Evaluation Engine
  details: Blaze is one of the few FHIR Servers including a fast CQL Evaluation engine
  icon: 🚀
  link: /cql-queries
- title: Blazingly Fast
  icon: 🔥
  details: Blaze has great performance, especially when uploading data
  link: /performance
- title: Terminology Service
  icon: 📖
  details: Blaze offers terminology services including LOINC and SNOMED CT
  link: /terminology-service
- title: Easy to Run
  icon: 🎂
  details: Blaze can be started with a single command, no further configuration required  
  link: /deployment
---
