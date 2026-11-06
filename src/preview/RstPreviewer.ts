/**
 * The page's side of the preview compiler: a Web Worker running Sphinx in Pyodide
 * (sphinx-worker.ts), with a Sphinx template loaded from a url.
 *
 * Starting costs seconds (Pyodide, Sphinx), so one previewer serves every doc using the
 * same template: get it with {@link RstPreviewer.of}.
 *
 *   const pv = RstPreviewer.of('sdoc-template/');
 *   const { pages } = await pv.build(assemble(forest[0]));
 *   const html = await pv.page('index.html');
 */
import type { RstFile } from '../doctree/rst-assembler';
import { workerSource } from './sphinx-worker';

export interface BuildResult {
	/** Sphinx's warnings, '' if none */
	log: string;
	/** the doc's built pages, e.g. ["index.html", "a1.html", ...] */
	pages: string[];
}

type Call = { resolve: (r: any) => void; reject: (e: Error) => void };

export class RstPreviewer {
	private static all = new Map<string, RstPreviewer>();

	/**
	 * The previewer of a template, started on first use.
	 * @param template url of the template folder (manifest.json, source/, _static/), relative to the page
	 */
	static of(template: string): RstPreviewer {
		const url = RstPreviewer.folder(template);
		let pv = RstPreviewer.all.get(url);
		if (!pv) RstPreviewer.all.set(url, (pv = new RstPreviewer(url)));
		return pv;
	}

	/** absolute url of a folder, ending with '/' */
	static folder(url: string) {
		const u = new URL(url, location.href).href;
		return u.endsWith('/') ? u : u + '/';
	}

	/** absolute url of the template folder; built pages load their _static/ from here */
	readonly template: string;
	/** last progress text, e.g. "Installing Sphinx…" */
	status = 'Starting…';
	/** resolved when Sphinx and the template are loaded */
	readonly ready: Promise<{ files: number; packages: string[] }>;

	private worker: Worker;
	private seq = 0;
	private calls = new Map<number, Call>();
	private listeners = new Set<(status: string) => void>();

	private constructor(template: string) {
		this.template = template;
		const src = URL.createObjectURL(new Blob([workerSource], { type: 'text/javascript' }));
		this.worker = new Worker(src);
		URL.revokeObjectURL(src);

		this.worker.onmessage = (e: MessageEvent) => {
			const m = e.data;
			if (m.status !== undefined) {
				this.status = m.status;
				this.listeners.forEach((l) => l(m.status));
			}
			const call = m.id !== undefined && this.calls.get(m.id);
			if (call) {
				this.calls.delete(m.id);
				m.ok ? call.resolve(m.r) : call.reject(new Error(m.err));
			}
		};
		this.worker.onerror = (e: ErrorEvent) => {
			this.status = `Worker error: ${e.message}`;
			this.listeners.forEach((l) => l(this.status));
		};

		this.ready = this.call('init', { template });
	}

	private call<T>(op: string, args: object): Promise<T> {
		const id = ++this.seq;
		return new Promise<T>((resolve, reject) => {
			this.calls.set(id, { resolve, reject });
			this.worker.postMessage({ id, op, args });
		});
	}

	/** listen to progress texts; returns the unsubscribe function */
	onStatus(listener: (status: string) => void): () => void {
		this.listeners.add(listener);
		return () => { this.listeners.delete(listener); };
	}

	/**
	 * Build the doc: the files replace the previous doc's (unchanged files aren't rewritten).
	 * Rejects with Sphinx's log if the build fails.
	 */
	build(files: RstFile[]): Promise<BuildResult> {
		return this.call('build', { files: files.map((f) => ({ path: f.path, rst: f.rst })) });
	}

	/** html of a built page, e.g. "index.html" */
	page(path: string): Promise<string> {
		return this.call('page', { path });
	}
}
