import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ParagraphEditor } from '../editor/components/ParagraphEditor';
import '../editor/styles/theme-roles.css';
import '../editor/styles/editor.css';
import './test.css';
import { inlineCases } from './cases/inline';
import { run } from './runner';

const results = run(inlineCases);
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
