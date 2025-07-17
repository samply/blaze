<script lang="ts">
  import type { ActionData } from './$types';

  import InputText from './../input-text.svelte';
  import InputTextError from './../input-text-error.svelte';
  import SubmitButtons from './../submit-buttons.svelte';

  interface Props {
    form: ActionData;
  }

  let { form }: Props = $props();
</script>

<svelte:head>
  <title>Create New (Re)Index a Search Parameter Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <form method="POST">
    <div class="mt-6 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6">
      <div class="col-span-full">
        <label for="search-param-url" class="block text-sm/6 font-medium leading-6 text-gray-900"
          >Search Param URL</label
        >
        {#if form?.incorrect}
          <InputTextError name="search-param-url" value={form?.searchParamUrl ?? ''} />
          <p class="mt-2 text-sm text-red-600">{form.msg}</p>
        {:else}
          <InputText name="search-param-url" value={form?.searchParamUrl ?? ''} />
        {/if}
      </div>
    </div>
    <SubmitButtons />
  </form>
</main>
