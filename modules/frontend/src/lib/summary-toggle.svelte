<script lang="ts">
  import { invalidateAll } from '$app/navigation';
  import { page } from '$app/state';
  import {
    defaultSummarySettings,
    storeSummarySettings,
    withSummaryForType,
    type SummarySettings
  } from '$lib/summary.js';
  import Toggle from '$lib/tailwind/toggle.svelte';

  interface Props {
    /** Whether summary mode is currently enabled for this view. */
    enabled: boolean;
    /** Resource type the choice applies to; omitted for system-wide views. */
    type?: string;
  }

  let { enabled, type }: Props = $props();

  function currentSettings(): SummarySettings {
    return (page.data.summarySettings as SummarySettings | undefined) ?? defaultSummarySettings;
  }

  async function onChange(checked: boolean) {
    const settings = currentSettings();
    const updated =
      type !== undefined
        ? withSummaryForType(settings, type, checked)
        : { ...settings, default: checked };
    storeSummarySettings(updated);
    await invalidateAll();
  }
</script>

<Toggle id="summary" label="Summary" checked={enabled} onchange={onChange} />
