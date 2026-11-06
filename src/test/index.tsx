import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import 'katex/dist/katex.min.css';
import { ParagraphEditor } from '../editor';
import './test.css';
import { allCases } from './cases';
import { run } from './runner';
import { runAssemble } from './cases/rst-assembler';

const results = [...run(allCases), ...runAssemble()];
const passed = results.filter((r) => r.pass).length;

// for headless runs (e.g. Playwright): read document.title or window.__results
document.title = `${passed === results.length ? 'PASS' : 'FAIL'} ${passed}/${results.length}`;
(window as any).__results = results;
results.filter((r) => !r.pass).forEach((r) => console.error('FAIL', r));

function Manual() {
  const [rst, setRst] = useState('');
  return (
    <>
      <ParagraphEditor onChange={(_, r) => setRst(r.rst)} />
      <pre>{rst}</pre>
    </>
  );
}

function TestPage() {
  return (
    <>
      <h2>
        RST serializer: {passed}/{results.length} passed
      </h2>
      <table className="results">
        <thead>
          <tr><th></th><th>case</th><th>expected</th><th>actual</th></tr>
        </thead>
        <tbody>
          {results.map((r) => (
            <tr key={r.name} className={r.pass ? 'pass' : 'fail'}>
              <td>{r.pass ? '✔' : '✘'}</td>
              <td>{r.name}</td>
              <td><code>{r.expected}</code></td>
              <td><code>{r.error ?? r.actual}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
      <h2>Manual check</h2>
      <Manual />
    </>
  );
}

createRoot(document.getElementById('root')!).render(<TestPage />);
