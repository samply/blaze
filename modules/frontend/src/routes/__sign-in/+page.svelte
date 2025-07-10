<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';
</script>

<svelte:head>
  <title>Sign-In - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <div class="flex justify-center mt-10">
    <form class="w-full max-w-sm space-y-6" method="POST" action="{base}/__sign-in">
      <input type="hidden" name="providerId" value="keycloak" />
      <input type="hidden" name="redirectTo" value={page.url.searchParams.get('redirect')} />
      <button
        type="submit"
        class="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold leading-6 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >Sign in with Keycloak</button
      >
    </form>
  </div>
</main>
