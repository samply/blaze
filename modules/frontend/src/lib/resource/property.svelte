<script lang="ts">
  import {
    type FhirProperty,
    isPrimitive,
    type FhirPrimitive,
    type FhirObject
  } from './resource-card.js';

  import type {
    Attachment,
    Identifier,
    HumanName,
    Address,
    ContactPoint,
    Reference,
    Dosage
  } from 'fhir/r5';

  import PrimitiveValue from './primitive-value.svelte';
  import ComplexValue from './complex-value.svelte';
  import AttachmentValues from '$lib/values/attachment.svelte';
  import IdentifierValues from '$lib/values/identifier.svelte';
  import HumanNameValues from '$lib/values/human-name.svelte';
  import AddressValues from '$lib/values/address.svelte';
  import ContactPointValues from '$lib/values/contact-point.svelte';
  import ReferenceValues from '$lib/values/reference.svelte';
  import DosageValues from '$lib/values/dosage.svelte';

  import { toTitleCase } from '$lib/util.js';

  interface Props {
    property: FhirProperty;
  }

  let { property }: Props = $props();

  function toArray<T>(x: T[] | T): T[] {
    return Array.isArray(x) ? x : [x];
  }

  const singlePrimitiveValue =
    isPrimitive(property.type) && !Array.isArray(property.value)
      ? (property.value as FhirPrimitive)
      : undefined;

  const multiplePrimitiveValues =
    isPrimitive(property.type) && Array.isArray(property.value)
      ? (property.value as FhirPrimitive[])
      : undefined;

  const multipleComplexValues = !isPrimitive(property.type)
    ? (toArray(property.value) as FhirObject[])
    : undefined;

  function asAttachmentValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<Attachment>[];
  }

  function asIdentifierValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<Identifier>[];
  }

  function asHumanNameValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<HumanName>[];
  }

  function asAddressValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<Address>[];
  }

  function asContactPointValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<ContactPoint>[];
  }

  function asReferenceValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<Reference>[];
  }

  function asDosageValues(property: FhirProperty) {
    return toArray(property.value) as FhirObject<Dosage>[];
  }
</script>

<div class="py-4 sm:grid sm:grid-cols-4 sm:gap-4 sm:px-6 sm:py-5" role="listitem">
  <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">
    {toTitleCase(property.humanName ?? property.name)}
  </dt>
  <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:col-span-3 sm:mt-0">
    {#if singlePrimitiveValue}
      <PrimitiveValue value={singlePrimitiveValue} />
    {:else if multiplePrimitiveValues}
      {#each multiplePrimitiveValues as primitiveValue}
        <PrimitiveValue value={primitiveValue} />
      {/each}
    {:else if multipleComplexValues}
      {#if property.type.code === 'Attachment'}
        <AttachmentValues values={asAttachmentValues(property)} />
      {:else if property.type.code === 'Identifier'}
        <IdentifierValues values={asIdentifierValues(property)} />
      {:else if property.type.code === 'HumanName'}
        <HumanNameValues values={asHumanNameValues(property)} />
      {:else if property.type.code === 'Address'}
        <AddressValues values={asAddressValues(property)} />
      {:else if property.type.code === 'ContactPoint'}
        <ContactPointValues values={asContactPointValues(property)} />
      {:else if property.type.code === 'Reference'}
        <ReferenceValues values={asReferenceValues(property)} />
      {:else if property.type.code === 'Dosage'}
        <DosageValues values={asDosageValues(property)} />
      {:else if property.type.code === 'Element'}
        <p class="text-gray-500 dark:text-gray-400">
          {multipleComplexValues.length > 0 ? '<elements>' : '<element>'}
        </p>
      {:else if property.type.code === 'BackboneElement'}
        <p class="text-gray-500 dark:text-gray-400">
          {multipleComplexValues.length > 0 ? '<backbone-elements>' : '<backbone-element>'}
        </p>
      {:else if property.type.code === 'Extension'}
        <p class="text-gray-500 dark:text-gray-400">
          {multipleComplexValues.length > 0 ? '<extensions>' : '<extension>'}
        </p>
      {:else if property.type.code === 'Resource'}
        <p class="text-gray-500 dark:text-gray-400">
          {multipleComplexValues.length > 0 ? '<resources>' : '<resource>'}
        </p>
      {:else}
        {#each multipleComplexValues as complexValue}
          <ComplexValue value={complexValue} />
        {/each}
      {/if}
    {/if}
  </dd>
</div>
