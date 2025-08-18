<script lang="ts">
  import ExternalLink from '$lib/values/util/external-link.svelte';
  import CardHeading from '$lib/tailwind/card/heading.svelte';

  interface Props {
    diagnostics: string;
  }

  let { diagnostics }: Props = $props();

  type Plan = { type: string; scanType: string; scans: string; seeks: string };

  function parsePlan(raw: string) {
    const plan: Partial<Plan> = { type: 'type' };
    for (const part of raw.split(';')) {
      const [type, value] = part.split(':').map((s) => s.trim());
      if (type == 'TYPE') {
        plan.type = value;
      } else if (type == 'SEEKS') {
        plan.seeks = value.replace('NONE', '-');
      } else {
        plan.scanType = type.replace('SCANS', 'Scans ');
        plan.scans = value.replace('NONE', '-');
      }
    }
    return plan as Plan;
  }

  let { type, scanType, scans, seeks } = parsePlan(diagnostics);
</script>

<div class="my-2 py-2 px-4 sm:px-6 bg-gray-50 rounded-lg">
  <div class="flex items-center justify-between text-sm/6">
    <h2 class="text-base font-semibold text-gray-900">Query Plan</h2>
  </div>
  <p class="mt-1 text-sm text-gray-500">
    How the server is executing the FHIR search query. <ExternalLink
      href="https://samply.github.io/blaze/api/interaction/search-type.html#query-plan"
      title="Query plan documentation">See Documentation</ExternalLink
    >
  </p>
  <ul role="list" class="my-4 grid grid-cols-3 gap-4 divide-gray-200">
    <li>
      <CardHeading title="Query Type" subtitle={type}>
        <span
          class="inline-flex size-10 items-center justify-center rounded-lg bg-indigo-200 text-indigo-600"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
            class="size-6"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m21 7.5-2.25-1.313M21 7.5v2.25m0-2.25-2.25 1.313M3 7.5l2.25-1.313M3 7.5l2.25 1.313M3 7.5v2.25m9 3 2.25-1.313M12 12.75l-2.25-1.313M12 12.75V15m0 6.75 2.25-1.313M12 21.75V19.5m0 2.25-2.25-1.313m0-16.875L12 2.25l2.25 1.313M21 14.25v2.25l-2.25 1.313m-13.5 0L3 16.5v-2.25"
            />
          </svg>
        </span>
      </CardHeading>
    </li>
    <li>
      <CardHeading title={scanType} subtitle={scans}>
        <span
          class="inline-flex size-10 items-center justify-center rounded-lg bg-green-200 text-green-600"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
            class="size-6 text-green-800"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 0 1 0 3.75H5.625a1.875 1.875 0 0 1 0-3.75Z"
            />
          </svg>
        </span>
      </CardHeading>
    </li>
    <li>
      <CardHeading title="Seeks" subtitle={seeks}>
        <span
          class="inline-flex size-10 items-center justify-center rounded-lg bg-purple-200 text-purple-600"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
            class="size-6"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z"
            />
          </svg>
        </span>
      </CardHeading>
    </li>
  </ul>
</div>
