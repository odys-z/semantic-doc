/**
 * `npm test`: run all cases in Node (no browser), exit 1 on failure.
 * The same cases are shown in the browser at /test.html (npm start).
 */
import { inlineCases } from './cases/inline';
import { run } from './runner';

const results = run(inlineCases);
let failed = 0;

for (const r of results) {
  if (r.pass) {
    console.log(`  ✔ ${r.name}`);
  } else {
    failed++;
    console.log(`  ✘ ${r.name}`);
    console.log(`      expected: ${r.expected}`);
    console.log(`      actual:   ${r.error ?? r.actual}`);
  }
}

console.log(`\n${results.length - failed}/${results.length} passed`);
process.exitCode = failed ? 1 : 0;
