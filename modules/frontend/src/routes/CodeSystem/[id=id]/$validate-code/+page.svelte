<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import Form from '$lib/tailwind/form.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import TextField from '$lib/tailwind/form/text-field.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';
  import ResultList from '../../result-list.svelte';

  import { title } from '$lib/resource.js';

  let { data, form }: PageProps = $props();
</script>

<svelte:head>
  <title>$validate-code - {title(data.codeSystem)} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType type="CodeSystem" />
    <BreadcrumbEntryResource type="CodeSystem" resource={data.codeSystem} />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500">$validate-code</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <h2 class="text-base/7 font-semibold text-gray-900">
    {title(data.codeSystem)}
  </h2>
  {#if data.codeSystem.description}
    <p class="mt-1 max-w-2xl text-sm/6 text-gray-600">{data.codeSystem.description}</p>
  {/if}

  <Form class="mt-4">
    <Section name="Parameters">
      <TextField id="code" label="Code" value={form?.code} />
      <TextField id="display" label="Display" value={form?.display} />
      <TextField id="displayLanguage" label="Display Language" value={form?.displayLanguage} />
    </Section>
    {#snippet buttons()}
      <SubmitButton name="Submit" />
    {/snippet}
  </Form>

  {#if form?.incorrect}
    <p class="text-red-600">{form.msg}</p>
  {/if}

  {#if form?.result}
    <ResultList parameters={form?.result} />
  {/if}
</main>
