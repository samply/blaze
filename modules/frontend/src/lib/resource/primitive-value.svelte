<script lang="ts">
  import type { Element } from 'fhir/r4';
  import type { ElementType } from '#lib/element-types.js';
  import { dataAbsentReason, hasOtherExtensions } from '#lib/values/data-absent-reason.js';

  import Canonical from '#lib/values/canonical.svelte';
  import Date from '#lib/values/date.svelte';
  import Time from '#lib/values/time.svelte';
  import DateTime from '#lib/values/date-time.svelte';
  import Badge from '#lib/tailwind/badge.svelte';

  interface Props {
    type: ElementType;
    value: unknown;
    element?: Element;
  }

  let { type, value, element }: Props = $props();

  let reason = $derived(dataAbsentReason(element));
</script>

{#if typeof value !== 'string'}
  {value}
{:else if type.code === 'canonical'}
  <Canonical {type} {value} />
{:else if type.code === 'date'}
  <Date {value} />
{:else if type.code === 'dateTime' || type.code === 'instant'}
  <DateTime {value} />
{:else if type.code === 'time'}
  <Time {value} />
{:else}
  {value}
{/if}

{#if reason !== undefined}
  <Badge value="absent: {reason}" title="Data absent reason" />
{/if}

{#if hasOtherExtensions(element)}
  (has extensions)
{/if}
