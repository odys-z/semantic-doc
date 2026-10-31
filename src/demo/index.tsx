import React from 'react';
import { createRoot } from 'react-dom/client';
import 'katex/dist/katex.min.css'; // host app provides KaTeX CSS (see editor/index.ts)
import './demo.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(<App />);
