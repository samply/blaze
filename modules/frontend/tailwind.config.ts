import type { Config } from 'tailwindcss';
import defaultTheme from 'tailwindcss/defaultTheme';
import forms from '@tailwindcss/forms';

export default {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Lexend Variable', ...defaultTheme.fontFamily.sans]
      }
    }
  },
  plugins: [forms]
} satisfies Config;
