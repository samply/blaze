<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { signIn } from '#lib/auth-client.js';

  let error = $state<string>();

  async function onsubmit(event: SubmitEvent) {
    event.preventDefault();
    error = await signIn(page.url.searchParams.get('redirect') ?? resolve('/'));
  }
</script>

<svelte:head>
  <title>Sign-In - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <div class="mt-10 flex justify-center">
    <form class="mt-6 w-full max-w-sm" {onsubmit}>
      <button
        type="submit"
        class="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-1.5 text-sm leading-6 font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 enabled:cursor-pointer"
        >Sign in with Keycloak</button
      >
      {#if error}
        <p class="mt-2 text-sm text-red-600 dark:text-red-400">{error}</p>
      {/if}
    </form>
  </div>
</main>
