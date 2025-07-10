<script lang="ts">
  import { onMount } from 'svelte';

  function secondsPassed(now: number, value: string) {
    return (now - Date.parse(value)) / 1000;
  }

  const rtf = new Intl.RelativeTimeFormat('en', {
    numeric: 'auto',
    style: 'long'
  });

  function timeAgo(now: number, value: string) {
    const seconds = secondsPassed(now, value);
    if (seconds > 86400) {
      return rtf.format(Math.round(-seconds / 86400), 'day');
    }
    if (seconds > 3600) {
      return rtf.format(Math.round(-seconds / 3600), 'hour');
    }
    if (seconds > 60) {
      return rtf.format(Math.round(-seconds / 60), 'minute');
    }
    return rtf.format(Math.round(-seconds), 'second');
  }

  interface Props {
    value: string;
  }

  let { value }: Props = $props();
  let now = $state(Date.now());

  onMount(() => {
    const interval = setInterval(() => {
      now = Date.now();
    }, 1000);

    return () => clearInterval(interval);
  });
</script>

<time datetime={value}>{timeAgo(now, value)}</time>
