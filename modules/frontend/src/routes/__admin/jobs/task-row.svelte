<script lang="ts">
  import { resolve } from '$app/paths';
  import { type Job } from '$lib/jobs';
  import Status from '$lib/jobs/status-round.svelte';
  import TimeAgo from '$lib/time-ago.svelte';
  import type { SummaryJob } from './+page.server';
  import humanizeDuration from 'humanize-duration';
  import { Calendar, Clock, EllipsisVertical } from 'svelte-heros-v2';

  function actionsAvailable(job: Job) {
    return job.status === 'in-progress' || job.status === 'on-hold';
  }

  interface Props {
    job: SummaryJob;
  }

  let { job }: Props = $props();
  let actionMenuOpen = $state(false);
</script>

<li class="flex justify-between gap-x-6 py-5">
  <div class="flex min-w-0 gap-x-4">
    <Status {job} />
    <div class="min-w-0 flex-auto">
      <p class="text-sm font-semibold leading-6 text-gray-900">
        <a
          href={resolve('/__admin/jobs/[type]/[id=id]', { type: job.type.code, id: job.id })}
          class="hover:underline">{job.type.display}</a
        >
      </p>
      <p class="mt-1 flex text-xs leading-5 text-gray-500">
        #{job.number}
        {job.detail}
      </p>
    </div>
  </div>
  <div class="flex shrink-0 items-center gap-x-6">
    <div class="hidden sm:flex sm:flex-col sm:items-end">
      <p class="text-xs leading-6 text-gray-500">
        <Calendar class="inline size-4" />
        <TimeAgo value={job.authoredOn} />
      </p>
      <p class="text-xs leading-6 text-gray-500">
        <Clock class="inline size-4" />
        {#if job.processingDuration}
          {humanizeDuration(job.processingDuration * 1000, { round: true })}
        {/if}
      </p>
    </div>
    <div class="relative flex-none">
      <button
        type="button"
        class="-m-2.5 block p-2.5 text-gray-500 hover:text-gray-900 enabled:cursor-pointer"
        id="options-menu-0-button"
        aria-expanded="false"
        aria-haspopup="true"
        onclick={() => (actionMenuOpen = !actionMenuOpen)}
        disabled={!actionsAvailable(job)}
      >
        <span class="sr-only">Open options</span>
        <EllipsisVertical variation="mini" class="inline size-5" />
      </button>

      <!--
				Dropdown menu, show/hide based on menu state.

				Entering: "transition ease-out duration-100"
					From: "transform opacity-0 scale-95"
					To: "transform opacity-100 scale-100"
				Leaving: "transition ease-in duration-75"
					From: "transform opacity-100 scale-100"
					To: "transform opacity-0 scale-95"
			-->
      <div
        class="absolute flex flex-col items-stretch right-0 z-10 mt-2 w-32 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none"
        class:hidden={!actionMenuOpen}
        role="menu"
        aria-orientation="vertical"
        aria-labelledby="options-menu-0-button"
        tabindex="-1"
      >
        {#if job.status === 'in-progress' && (job.type.code === 'async-interaction' || job.type.code === 're-index')}
          {#if job.type.code === 'async-interaction'}
            <form method="POST" action="?/cancel" class="flex flex-col items-stretch">
              <input type="hidden" name="job-id" value={job.id} />
              <button
                type="submit"
                class="block px-3 py-1 text-sm leading-6 text-left text-gray-900 hover:bg-gray-50 enabled:cursor-pointer"
                role="menuitem"
                tabindex="-1"
                id="options-menu-0-item-0"
                >Cancel
              </button>
            </form>
          {:else if job.type.code === 're-index'}
            <form method="POST" action="?/pause" class="flex flex-col items-stretch">
              <input type="hidden" name="job-id" value={job.id} />
              <button
                type="submit"
                class="block px-3 py-1 text-sm leading-6 text-left text-gray-900 hover:bg-gray-50 enabled:cursor-pointer"
                role="menuitem"
                tabindex="-1"
                id="options-menu-0-item-0"
                >Pause
              </button>
            </form>
          {/if}
        {/if}
        {#if job.status === 'on-hold'}
          <form method="POST" action="?/resume" class="flex flex-col items-stretch">
            <input type="hidden" name="job-id" value={job.id} />
            <button
              type="submit"
              class="block px-3 py-1 text-sm leading-6 text-left text-gray-900 hover:bg-gray-50 enabled:cursor-pointer"
              role="menuitem"
              tabindex="-1"
              id="options-menu-0-item-0"
              >Resume
            </button>
          </form>
        {/if}
      </div>
    </div>
  </div>
</li>
