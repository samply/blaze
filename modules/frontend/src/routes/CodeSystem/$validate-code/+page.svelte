<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import Form from '$lib/tailwind/form.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import TextField from '$lib/tailwind/form/text-field.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';
  import ResultList from '../result-list.svelte';

  let { form }: PageProps = $props();
</script>

<svelte:head>
  <title>$validate-code - CodeSystem - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType type="CodeSystem" />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">$validate-code</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <Form class="mt-4">
    <Section name="Parameters">
      <TextField id="url" label="URL" value={form?.url} />
      <TextField id="version" label="Version" value={form?.version} />
      <TextField id="code" label="Code" value={form?.code} />
      <TextField id="display" label="Display" value={form?.display} />
      <TextField id="displayLanguage" label="Display Language" value={form?.displayLanguage} />
    </Section>
    {#snippet buttons()}
      <SubmitButton name="Submit" />
    {/snippet}
  </Form>

  {#if form?.incorrect}
    <p class="text-red-600 dark:text-red-400">{form.msg}</p>
  {/if}

  {#if form?.result}
    <ResultList parameters={form?.result} />
  {/if}
</main>
