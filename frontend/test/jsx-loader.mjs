/*
 * Lets `node --test` import .jsx files: each one is compiled with the JSX transform that ships
 * inside Vite (rolldown), so tests need no extra packages. Used by `npm test`.
 */
import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { transformSync } from 'rolldown/experimental';

registerHooks({
  load(url, context, nextLoad) {
    if (!url.endsWith('.jsx')) return nextLoad(url, context);
    const path = fileURLToPath(url);
    const result = transformSync(path, readFileSync(path, 'utf8'), { jsx: { runtime: 'automatic' } });
    if (result.errors?.length) throw new Error(`${path}: ${result.errors.map(error => error.message).join('\n')}`);
    return { format: 'module', source: result.code, shortCircuit: true };
  },
});
