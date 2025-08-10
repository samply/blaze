<script lang="ts">
  import type { Snippet } from 'svelte';

  import '../app.css';
  import '@fontsource-variable/lexend';
  import { page } from '$app/state';
  import { fade } from 'svelte/transition';
  import { onNavigate } from '$app/navigation';
  import { signOut } from '@auth/sveltekit/client';
  import NavItem from '$lib/nav-item.svelte';
  import { asset } from '$app/paths';

  interface Props {
    children?: Snippet;
  }

  let { children }: Props = $props();

  // https://svelte.dev/blog/view-transitions
  onNavigate((navigation) => {
    if (!document.startViewTransition) return;

    return new Promise((resolve) => {
      document.startViewTransition(async () => {
        resolve();
        await navigation.complete;
      });
    });
  });

  function isHomeRoute(routeId: string | null): boolean {
    return (
      routeId != null &&
      !routeId.startsWith('/_history') &&
      !routeId.startsWith('/__history-page') &&
      !routeId.startsWith('/metadata') &&
      !routeId.startsWith('/__admin')
    );
  }

  let userProfileOpen = $state(false);
</script>

<div class="min-h-full">
  <nav class="border-b border-gray-200 bg-white">
    <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="flex h-16 justify-between">
        <div class="flex">
          <div class="flex shrink-0 items-center">
            <img class="h-8 w-auto" src={asset('/blaze-logo.svg')} alt="Blaze" />
          </div>
          <!-- Navigation Menu -->
          <div class="hidden sm:-my-px sm:ml-6 sm:flex sm:space-x-8">
            <NavItem active={isHomeRoute(page.route.id)} id="/" label="Home" />
            <NavItem
              active={page.route.id?.startsWith('/_history') ||
                page.route.id?.startsWith('/__history-page')}
              id="/_history"
              label="History"
            />
            <NavItem
              active={page.route.id?.startsWith('/metadata')}
              id="/metadata"
              label="Metadata"
            />
            <NavItem active={page.route.id?.startsWith('/__admin')} id="/__admin" label="Admin" />
          </div>
        </div>

        <!-- User Profile -->
        <div class="flex ml-6 items-center">
          <div class="relative ml-3">
            <div>
              <button
                type="button"
                class="relative flex max-w-xs items-center rounded-full bg-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 enabled:cursor-pointer"
                id="user-menu-button"
                aria-expanded="false"
                aria-haspopup="true"
                onclick={() => (userProfileOpen = !userProfileOpen)}
                disabled={!page.data.session}
              >
                <span class="absolute -inset-1.5"></span>
                <span class="sr-only">Open user menu</span>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke-width="1.5"
                  stroke="currentColor"
                  class="w-8 h-8"
                  class:text-gray-900={page.data.session}
                  class:text-gray-500={!page.data.session}
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M17.982 18.725A7.488 7.488 0 0 0 12 15.75a7.488 7.488 0 0 0-5.982 2.975m11.963 0a9 9 0 1 0-11.963 0m11.963 0A8.966 8.966 0 0 1 12 21a8.966 8.966 0 0 1-5.982-2.275M15 9.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"
                  />
                </svg>
              </button>
            </div>

            <!--
                          Dropdown menu, show/hide based on menu state.

                          Entering: "transition ease-out duration-200"
                            From: "transform opacity-0 scale-95"
                            To: "transform opacity-100 scale-100"
                          Leaving: "transition ease-in duration-75"
                            From: "transform opacity-100 scale-100"
                            To: "transform opacity-0 scale-95"
                        -->
            {#if userProfileOpen}
              <div
                class="absolute flex flex-col items-stretch right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white py-1 shadow-lg ring-1 ring-black/5 focus:outline-none"
                role="menu"
                aria-orientation="vertical"
                aria-labelledby="user-menu-button"
                tabindex="-1"
                transition:fade={{ duration: 100 }}
              >
                <p class="block px-4 py-2 text-sm text-gray-900">{page.data.session?.user?.name}</p>
                <button
                  onclick={() => signOut()}
                  class="block px-4 py-2 text-sm text-left text-gray-700 hover:bg-gray-100 enabled:cursor-pointer"
                  role="menuitem"
                  tabindex="-1"
                  id="user-menu-item-sign-out"
                  >Sign out
                </button>
              </div>
            {/if}
          </div>
        </div>
      </div>
    </div>
  </nav>

  {@render children?.()}
</div>
