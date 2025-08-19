<script lang="ts">
  import type { Parameters } from 'fhir/r5';

  import { parameter } from '$lib/fhir';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-3-2.svelte';

  interface Props {
    parameters: Parameters;
  }

  let { parameters }: Props = $props();

  let result = $derived(parameter(parameters, 'result')?.valueBoolean);
</script>

<DescriptionList>
  <Row title="Result">
    {result}
  </Row>
  {#if !result}
    <Row title="Message">
      {parameter(parameters, 'message')?.valueString}
    </Row>
  {:else}
    {@const display = parameter(parameters, 'display')?.valueString}
    {#if display}
      <Row title="Display">
        {display}
      </Row>
    {/if}
    {@const code = parameter(parameters, 'code')?.valueCode}
    {#if code}
      <Row title="Code">
        {code}
      </Row>
    {/if}
    {@const system = parameter(parameters, 'system')?.valueUri}
    {#if system}
      <Row title="System">
        {system}
      </Row>
    {/if}
    {@const version = parameter(parameters, 'version')?.valueString}
    {#if version}
      <Row title="Version">
        {version}
      </Row>
    {/if}
  {/if}
</DescriptionList>
