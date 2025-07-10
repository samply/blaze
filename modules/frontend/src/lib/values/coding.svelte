<script lang="ts">
  import type { Coding } from 'fhir/r4';
  import ExternalLink from './util/external-link.svelte';

  interface Props {
    value: Coding;
  }

  let { value }: Props = $props();
</script>

{#if value.system}
  {#if ['http://snomed.info/sct', 'http://loinc.org'].includes(value.system)}
    <p>
      {#if value.code}
        <ExternalLink href="{value.system}/{value.code}" title="{value.system}/{value.code}"
          >{#if value.display}{value.display}{:else}{value.code}{/if}</ExternalLink
        >
      {:else}
        <ExternalLink href={value.system} title={value.system}
          >{#if value.display}{value.display}{:else}{value.system}{/if}</ExternalLink
        >
      {/if}
    </p>
  {:else if value.system.startsWith('http://terminology.hl7.org/CodeSystem') || value.system === 'http://www.nlm.nih.gov/research/umls/rxnorm'}
    <p>
      <ExternalLink href={value.system} title={value.system}
        >{#if value.display}{value.display}{:else if value.code}{value.code}{:else}{value.system}{/if}</ExternalLink
      >
    </p>
  {:else}
    <p>
      {#if value.display}{value.display}{:else if value.code}{value.code}{:else}{value.system}{/if}
    </p>
  {/if}
{:else if value.display}
  {value.display}
{:else if value.code}
  {value.code}
{:else}
  {'<not-available>'}
{/if}
