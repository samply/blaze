<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
</script>

<svelte:head>
  <title>Sign-In - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <div class="mt-10 flex justify-center">
    <form class="mt-6 w-full max-w-sm" method="POST" action={resolve('/__sign-in')}>
      <input type="hidden" name="providerId" value="keycloak" />
      <input type="hidden" name="redirectTo" value={page.url.searchParams.get('redirect')} />
      <button
        type="submit"
        class="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-1.5 text-sm leading-6 font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 enabled:cursor-pointer"
        >Sign in with Keycloak</button
      >
    </form>
  </div>
</main>
