<script lang="ts">
  import { resolve } from '$app/paths';
  import { Home } from 'svelte-heros-v2';
</script>

<li>
  <div>
    <a href={resolve('/')} class="text-gray-400 hover:text-gray-500 dark:text-gray-400">
      <Home variation="mini" class="size-5 shrink-0" />
      <span class="sr-only">Home</span>
    </a>
  </div>
</li>
