<script lang="ts">
  import type { Property } from './properties.js';
  import { isPrimitive } from '#lib/element-types.js';

  import type {
    Attachment,
    Identifier,
    HumanName,
    Address,
    ContactPoint,
    Reference,
    Dosage
  } from 'fhir/r4';

  import PrimitiveValue from './primitive-value.svelte';
  import ComplexValue from './complex-value.svelte';
  import AttachmentValues from '#lib/values/attachment.svelte';
  import IdentifierValues from '#lib/values/identifier.svelte';
  import HumanNameValues from '#lib/values/human-name.svelte';
  import AddressValues from '#lib/values/address.svelte';
  import ContactPointValues from '#lib/values/contact-point.svelte';
  import ReferenceValues from '#lib/values/reference.svelte';
  import DosageValues from '#lib/values/dosage.svelte';

  import { toTitleCase } from '#lib/util.js';

  interface Props {
    property: Property;
  }

  let { property }: Props = $props();

  let placeholder = $derived(property.values.length > 1 ? 's' : '');
</script>

<div class="py-4 sm:grid sm:grid-cols-4 sm:gap-4 sm:px-6 sm:py-5" role="listitem">
  <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">
    {toTitleCase(property.humanName)}
  </dt>
  <dd class="mt-1 text-sm text-gray-900 sm:col-span-3 sm:mt-0 dark:text-gray-100">
    {#if isPrimitive(property.type)}
      {#each property.values as value, index (index)}
        <PrimitiveValue type={property.type} {value} element={property.elements[index]} />
      {/each}
    {:else if property.type.code === 'Attachment'}
      <AttachmentValues values={property.values as Attachment[]} />
    {:else if property.type.code === 'Identifier'}
      <IdentifierValues values={property.values as Identifier[]} />
    {:else if property.type.code === 'HumanName'}
      <HumanNameValues values={property.values as HumanName[]} />
    {:else if property.type.code === 'Address'}
      <AddressValues values={property.values as Address[]} />
    {:else if property.type.code === 'ContactPoint'}
      <ContactPointValues values={property.values as ContactPoint[]} />
    {:else if property.type.code === 'Reference'}
      <ReferenceValues values={property.values as Reference[]} />
    {:else if property.type.code === 'Dosage'}
      <DosageValues values={property.values as Dosage[]} />
    {:else if property.type.code === 'BackboneElement'}
      <p class="text-gray-500 dark:text-gray-400">&lt;backbone-element{placeholder}&gt;</p>
    {:else if property.type.code === 'Extension'}
      <p class="text-gray-500 dark:text-gray-400">&lt;extension{placeholder}&gt;</p>
    {:else if property.type.code === 'Resource'}
      <p class="text-gray-500 dark:text-gray-400">&lt;resource{placeholder}&gt;</p>
    {:else}
      {#each property.values as value, index (index)}
        <ComplexValue type={property.type} {value} />
      {/each}
    {/if}
  </dd>
</div>
