<script lang="ts">
  import type { CodeableConcept, Coding, Meta, Money, Period, Quantity } from 'fhir/r4';
  import type { ElementType } from '#lib/element-types.js';

  import CodeableConceptValue from '#lib/values/codeable-concept.svelte';
  import CodingValue from '#lib/values/coding.svelte';
  import MetaValue from '#lib/values/meta.svelte';
  import MoneyValue from '#lib/values/money.svelte';
  import PeriodValue from '#lib/values/period.svelte';
  import QuantityValue from '#lib/values/quantity.svelte';

  interface Props {
    type: ElementType;
    value: unknown;
  }

  let { type, value }: Props = $props();
</script>

{#if type.code === 'CodeableConcept'}
  <CodeableConceptValue value={value as CodeableConcept} />
{:else if type.code === 'Coding'}
  <CodingValue value={value as Coding} />
{:else if type.code === 'Meta'}
  <MetaValue value={value as Meta} />
{:else if type.code === 'Money'}
  <MoneyValue value={value as Money} />
{:else if type.code === 'Period'}
  <PeriodValue value={value as Period} />
{:else if type.code === 'Quantity'}
  <QuantityValue value={value as Quantity} />
{:else}
  ({type.code}) {value}
{/if}
