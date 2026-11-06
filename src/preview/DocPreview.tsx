/**
 * Html preview of a doc: its RST files built by Sphinx in the browser (RstPreviewer),
 * shown in an iframe.
 *
 * The built page is shown as a blob, with <base href> at the template folder, where the
 * theme's _static/ is (all the doc's pages are at the top of the build). Links between
 * the doc's pages are followed inside the preview; other sites open in a new tab.
 *
 * Rebuilds `delay` ms after files change, staying on the page shown if it's still there.
 * Pass a new files array only when the doc changed (e.g. memoized on a revision).
 */
import React from 'react';
import type { RstFile } from '../doctree/rst-assembler';
import { RstPreviewer } from './RstPreviewer';
import './preview.css';

export interface DocPreviewProps {
	/** url of the Sphinx template folder, relative to the page, e.g. "sdoc-template/" */
	template: string;
	/** the doc's files, from assemble() */
	files: RstFile[];
	/** ms to wait after a change before building; default 800 */
	delay?: number;
	className?: string;
	style?: React.CSSProperties;
}

const html = (body: string) => `<!DOCTYPE html><html><body>${body}</body></html>`;
const esc = (s: string) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]!);

export function DocPreview({ template, files, delay = 800, className, style }: DocPreviewProps) {
	const pv = React.useMemo(() => RstPreviewer.of(template), [template]);
	const [status, setStatus] = React.useState(pv.status);
	const frame = React.useRef<HTMLIFrameElement>(null);
	/** page on show, and the #fragment to scroll to once loaded */
	const shown = React.useRef({ page: 'index.html', hash: '' });
	const blob = React.useRef<string | undefined>(undefined);

	React.useEffect(() => pv.onStatus(setStatus), [pv]);

	const showHtml = React.useCallback((page: string) => {
		const f = frame.current;
		if (!f) return;
		const prev = blob.current;
		blob.current = URL.createObjectURL(new Blob(
			[page.replace(/<head>/i, `<head>\n<base href="${pv.template}">`)], { type: 'text/html' }));
		f.src = blob.current;
		if (prev) f.addEventListener('load', () => URL.revokeObjectURL(prev), { once: true });
	}, [pv]);

	const show = React.useCallback(async (page: string, hash = '') => {
		shown.current = { page, hash };
		showHtml(await pv.page(page));
	}, [pv, showHtml]);

	// build after changes
	React.useEffect(() => {
		let live = true;
		const t = setTimeout(async () => {
			try {
				const { pages, log } = await pv.build(files);
				if (log.trim()) console.warn('[semantic-doc preview]\n' + log);
				if (live) await show(pages.includes(shown.current.page) ? shown.current.page : 'index.html', shown.current.hash);
			} catch (e) {
				if (live) showHtml(html(`<pre>${esc(String((e as Error).message ?? e))}</pre>`));
				setStatus('Failed, see the preview.');
			}
		}, delay);
		return () => { live = false; clearTimeout(t); };
	}, [pv, files, delay, show, showHtml]);

	React.useEffect(() => () => { if (blob.current) URL.revokeObjectURL(blob.current); }, []);

	// links in the page: the doc's pages are in the worker, not on the server
	const onLoad = React.useCallback(() => {
		const doc = frame.current?.contentDocument;
		if (!doc) return;
		doc.addEventListener('click', (e) => {
			const a = (e.target as Element).closest?.('a[href]');
			if (!a || e.defaultPrevented) return;
			const href = a.getAttribute('href')!;
			e.preventDefault();
			if (/^[a-z][a-z0-9+.-]*:/i.test(href) || href.startsWith('//')) {
				window.open((a as HTMLAnchorElement).href, '_blank', 'noopener');
				return;
			}
			const [path, hash = ''] = href.split('#');
			const page = path ? path.split('/').pop() || 'index.html' : shown.current.page;
			if (page === shown.current.page) scrollTo(doc, hash);
			else show(page, hash).catch(() => setStatus(`Not in the preview: ${page}`));
		});
		doc.addEventListener('submit', (e) => { e.preventDefault(); setStatus("Search isn't available in the preview."); });
		scrollTo(doc, shown.current.hash);
		shown.current.hash = '';
	}, [show]);

	return (
		<div className={`sd-preview ${className ?? ''}`} style={style}>
			<div className="sd-preview-status">{status}</div>
			<iframe ref={frame} className="sd-preview-frame" title="html preview" onLoad={onLoad} />
		</div>
	);
}

function scrollTo(doc: Document, hash: string) {
	const target = hash && doc.getElementById(decodeURIComponent(hash));
	if (target) target.scrollIntoView();
	else doc.defaultView?.scrollTo(0, 0);
}
