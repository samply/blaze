<script lang="ts">
  import type { Meta } from 'fhir/r4';
  import ExternalLink from './util/external-link.svelte';

  interface Props {
    value: Meta;
  }

  let { value }: Props = $props();
</script>

{#if value.profile && value.profile.length > 0}
  <div class="rounded-lg ring-1 ring-gray-300 dark:ring-gray-500">
    <table class="w-full table-fixed">
      <tbody>
        {#each value.profile as profile, i}
          <tr>
            {#if i === 0}
              <td
                class="table-cell w-1/3 px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
                rowspan={value.profile.length}>Profiles</td
              >
            {/if}
            <td class="table-cell px-5 py-3 text-sm text-gray-500 dark:text-gray-400">
              <ExternalLink href={profile}>{profile}</ExternalLink>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
