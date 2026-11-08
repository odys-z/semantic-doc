/**
 * `npm test`: run all cases in Node (no browser), exit 1 on failure.
 * The same cases are shown in the browser at /test.html (npm start).
 *
 * `node dist-test/run.js --json` prints the results as JSON instead, e.g. to
 * compile each case's RST with docutils.
 */
import { allCases } from './cases';
import { run } from './runner';
import { runAssemble } from './cases/rst-assembler';
import { runDocstree } from './cases/doc-stree';

const results = [...run(allCases), ...runAssemble(), ...runDocstree()];
let failed = results.filter((r) => !r.pass).length;

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(results, null, 2));
  process.exit(failed ? 1 : 0);
}
failed = 0;

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
