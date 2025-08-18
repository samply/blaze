<script lang="ts">
  import type { OperationOutcome, OperationOutcomeIssue } from 'fhir/r4';
  import QueryPlanCard from './query-plan-card.svelte';

  interface Props {
    outcome: OperationOutcome;
  }

  let { outcome }: Props = $props();

  function isPlan(issue: OperationOutcomeIssue) {
    return (
      issue.code === 'informational' &&
      issue.diagnostics?.includes('SCANS') &&
      issue.diagnostics?.includes('SEEKS')
    );
  }
</script>

{#if outcome.issue}
  {#each outcome.issue as issue ([issue.code, issue.details])}
    {#if isPlan(issue) && issue.diagnostics}
      <!-- TODO check for detail code = query-plan -->
      <QueryPlanCard diagnostics={issue.diagnostics}></QueryPlanCard>
    {/if}
  {/each}
{/if}
