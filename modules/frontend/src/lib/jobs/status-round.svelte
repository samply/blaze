<script lang="ts">
  import { type Job } from '$lib/jobs';
  import { CheckCircle, XMark, ChartPie, Pause, QuestionMarkCircle } from 'svelte-heros-v2';

  interface Props {
    job: Job;
  }

  let { job }: Props = $props();
</script>

{#if job.status === 'completed'}
  <CheckCircle variation="mini" class="w-6 h-12 text-center text-green-600" />
{:else if job.status === 'failed'}
  <XMark variation="mini" class="w-6 h-12 text-center text-red-600" />
{:else if job.status === 'cancelled'}
  <XMark variation="mini" class="w-6 h-12 text-center text-orange-600" />
{:else if job.status === 'in-progress'}
  <ChartPie variation="mini" class="animate-spin w-6 h-12 text-center text-yellow-400" />
{:else if job.status === 'on-hold'}
  <span
    class:text-blue-600={job.statusReason === 'paused'}
    class:text-yellow-400={job.statusReason === 'orderly-shutdown'}
  >
    <Pause variation="mini" class="w-6 h-12 text-center" />
  </span>
{:else}
  <QuestionMarkCircle variation="mini" class="w-6 h-12 text-center text-gray-600" />
{/if}
