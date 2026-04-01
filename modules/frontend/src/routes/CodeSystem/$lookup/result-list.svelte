<script lang="ts">
  import type { Parameters } from 'fhir/r4';

  import { parameter } from '$lib/fhir';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-3-2.svelte';

  interface Props {
    parameters: Parameters;
  }

  let { parameters }: Props = $props();

    console.log(parameters);
    console.log(parameter(parameters, 'version'));
  let result = $derived(parameter(parameters, 'result')?.valueBoolean);
</script>

<DescriptionList>
  <Row title="Name">
    {parameter(parameters, 'name')?.valueString}
  </Row>
  {@const version = parameter(parameters, 'version')?.valueString}
  {#if version}
    <Row title="Version">
      {version}
    </Row>
  {/if}
  {@const display = parameter(parameters, 'display')?.valueString}
  {#if display}
    <Row title="Display">
      {display}
    </Row>
  {/if}
  {@const definition = parameter(parameters, 'definition')?.valueString}
  {#if definition}
    <Row title="Definition">
      {definition}
    </Row>
  {/if}
</DescriptionList>
