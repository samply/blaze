<script lang="ts">
  import type { Meta } from 'fhir/r4';
  import ExternalLink from './util/external-link.svelte';

  interface Props {
    value: Meta;
  }

  let { value }: Props = $props();
</script>

{#if value.profile && value.profile.length > 0}
  <div class="ring-1 ring-gray-300 rounded-lg">
    <table class="table-fixed w-full">
      <tbody>
        {#each value.profile as profile, i}
          <tr>
            {#if i === 0}
              <td
                class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
                rowspan={value.profile.length}>Profiles</td
              >
            {/if}
            <td class="px-5 py-3 text-sm text-gray-500 table-cell">
              <ExternalLink href={profile}>{profile}</ExternalLink>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
