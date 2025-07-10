<script lang="ts">
  import type { FhirObject } from './resource-card.js';
  import type { CodeableConcept, Coding, Meta, Money, Period, Quantity } from 'fhir/r4';

  import CodeableConceptValue from '$lib/values/codeable-concept.svelte';
  import CodingValue from '$lib/values/coding.svelte';
  import MetaValue from '$lib/values/meta.svelte';
  import MoneyValue from '$lib/values/money.svelte';
  import PeriodValue from '$lib/values/period.svelte';
  import QuantityValue from '$lib/values/quantity.svelte';

  interface Props {
    value: FhirObject;
  }

  let { value }: Props = $props();

  function isCodeableConcept(value: FhirObject): value is FhirObject<CodeableConcept> {
    return value.type.code === 'CodeableConcept';
  }

  function isCoding(value: FhirObject): value is FhirObject<Coding> {
    return value.type.code === 'Coding';
  }

  function isMeta(value: FhirObject): value is FhirObject<Meta> {
    return value.type.code === 'Meta';
  }

  function isMoney(value: FhirObject<unknown>): value is FhirObject<Money> {
    return value.type.code === 'Money';
  }

  function isPeriod(value: FhirObject): value is FhirObject<Period> {
    return value.type.code === 'Period';
  }

  function isQuantity(value: FhirObject): value is FhirObject<Quantity> {
    return value.type.code === 'Quantity';
  }
</script>

{#if isCodeableConcept(value)}
  <CodeableConceptValue value={value.object} />
{:else if isCoding(value)}
  <CodingValue value={value.object} />
{:else if isMeta(value)}
  <MetaValue value={value.object} />
{:else if isMoney(value)}
  <MoneyValue value={value.object} />
{:else if isPeriod(value)}
  <PeriodValue value={value.object} />
{:else if isQuantity(value)}
  <QuantityValue value={value.object} />
{:else}
  ({value.type.code}) {value.object}
{/if}
