<script lang="ts">
  import type { Parameters } from 'fhir/r4';

  import { parameter, parameterParts, parameterValue } from '$lib/fhir';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-3-2.svelte';

  interface Props {
    parameters: Parameters;
  }

  let { parameters }: Props = $props();
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
  {@const properties = parameterParts(parameters, 'property')}
  {#if properties}
    {#each properties as property, i}
      <Row title={'Property[' + i + ']'}>
        <DescriptionList>
          {#each property.part as part}
            {@const paramValue = parameterValue(part)}
            {#if typeof paramValue == 'object'}
              <Row title={part.name}>
                <DescriptionList>
                  {#each Object.entries(paramValue) as [k, v]}
                    <Row title={k}>{v}</Row>
                  {/each}
                </DescriptionList>
              </Row>
            {:else}
              <Row title={part.name}>{paramValue}</Row>
            {/if}
          {/each}
        </DescriptionList>
      </Row>
    {/each}
  {/if}
  {@const designations = parameterParts(parameters, 'designation')}
  {#if designations}
    {#each designations as designation, i}
      <Row title={'Designation[' + i + ']'}>
        <DescriptionList>
          {#each designation.part as part}
            <Row title={part.name}>{parameterValue(part)}</Row>
          {/each}
        </DescriptionList>
      </Row>
    {/each}
  {/if}
</DescriptionList>
